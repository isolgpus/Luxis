package io.kiw.luxis.web.openapi;

import io.kiw.luxis.web.http.Method;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenApiSpecGenerator {
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private final OpenApiCollector collector;
    private final ObjectMapper objectMapper;
    private final JsonSchemaGenerator schemaGenerator;
    private String title = "API";
    private String version = "1.0.0";
    private String description;

    public OpenApiSpecGenerator(final OpenApiCollector collector, final ObjectMapper objectMapper) {
        this.collector = collector;
        this.objectMapper = objectMapper;
        this.schemaGenerator = new JsonSchemaGenerator(objectMapper);
    }

    public OpenApiSpecGenerator title(final String title) {
        this.title = title;
        return this;
    }

    public OpenApiSpecGenerator version(final String version) {
        this.version = version;
        return this;
    }

    public OpenApiSpecGenerator description(final String description) {
        this.description = description;
        return this;
    }

    public ObjectNode generate() {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.0.3");

        final ObjectNode info = root.putObject("info");
        info.put("title", title);
        info.put("version", version);
        if (description != null) {
            info.put("description", description);
        }

        final ObjectNode paths = root.putObject("paths");
        final Map<String, Map<String, RouteDescriptor>> groupedByPath = groupRoutes();

        for (final var pathEntry : groupedByPath.entrySet()) {
            final String openApiPath = convertPath(pathEntry.getKey());
            final ObjectNode pathItem = paths.putObject(openApiPath);

            for (final var methodEntry : pathEntry.getValue().entrySet()) {
                final String httpMethod = methodEntry.getKey().toLowerCase();
                final RouteDescriptor desc = methodEntry.getValue();
                final ObjectNode operation = pathItem.putObject(httpMethod);
                buildOperation(operation, desc, pathEntry.getKey());
            }
        }

        return root;
    }

    private Map<String, Map<String, RouteDescriptor>> groupRoutes() {
        final Map<String, Map<String, RouteDescriptor>> grouped = new LinkedHashMap<>();
        for (final RouteDescriptor route : collector.getRoutes()) {
            if (route.metadata() != null && route.metadata().hidden()) {
                continue;
            }
            if (route.kind() == RouteDescriptor.RouteKind.FILTER) {
                continue;
            }
            final String methodName = route.method() != null ? route.method().name() : "GET";
            grouped.computeIfAbsent(route.path(), k -> new LinkedHashMap<>())
                    .put(methodName, route);
        }
        return grouped;
    }

    private void buildOperation(final ObjectNode operation, final RouteDescriptor desc, final String rawPath) {
        if (desc.metadata() != null) {
            if (desc.metadata().summary() != null) {
                operation.put("summary", desc.metadata().summary());
            }
            if (desc.metadata().description() != null) {
                operation.put("description", desc.metadata().description());
            }
            if (!desc.metadata().tags().isEmpty()) {
                final ArrayNode tags = operation.putArray("tags");
                desc.metadata().tags().forEach(tags::add);
            }
        }

        operation.put("operationId", generateOperationId(desc.method(), rawPath));

        final ArrayNode parameters = buildPathParameters(rawPath, desc.metadata());
        if (parameters.size() > 0) {
            operation.set("parameters", parameters);
        }

        if (desc.method() != null && desc.method().canHaveABody() && desc.inputType() != null) {
            final ObjectNode requestBody = operation.putObject("requestBody");
            requestBody.put("required", true);
            final ObjectNode content = requestBody.putObject("content");
            final ObjectNode mediaType = content.putObject(desc.consumes());
            final ObjectNode schema = schemaGenerator.generateSchema(desc.inputType());
            if (schema != null) {
                mediaType.set("schema", schema);
            }
        } else if (desc.kind() == RouteDescriptor.RouteKind.UPLOAD) {
            final ObjectNode requestBody = operation.putObject("requestBody");
            requestBody.put("required", true);
            final ObjectNode content = requestBody.putObject("content");
            final ObjectNode mediaType = content.putObject("multipart/form-data");
            final ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
            schema.putObject("additionalProperties").put("type", "string").put("format", "binary");
            mediaType.set("schema", schema);
        }

        final ObjectNode responses = operation.putObject("responses");
        final ObjectNode successResponse = responses.putObject("200");
        final String responseDesc = (desc.metadata() != null && desc.metadata().responseDescription() != null)
                ? desc.metadata().responseDescription() : "Successful response";
        successResponse.put("description", responseDesc);

        if (desc.outputType() != null && desc.outputType() != Void.class) {
            final ObjectNode respContent = successResponse.putObject("content");
            if (desc.produces() != null) {
                final ObjectNode respMediaType = respContent.putObject(desc.produces());
                final ObjectNode schema = schemaGenerator.generateSchema(desc.outputType());
                if (schema != null) {
                    respMediaType.set("schema", schema);
                }
            }

        }
    }

    private String convertPath(final String path) {
        return PATH_PARAM_PATTERN.matcher(path).replaceAll("{$1}");
    }

    private String generateOperationId(final Method method, final String rawPath) {
        final String methodStr = method != null ? method.name().toLowerCase() : "get";
        final String pathPart = rawPath.replaceAll("[/:{}]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return methodStr + "_" + pathPart;
    }

    private ArrayNode buildPathParameters(final String path, final OpenApiMetadata metadata) {
        final ArrayNode params = objectMapper.createArrayNode();
        final Matcher m = PATH_PARAM_PATTERN.matcher(path);
        while (m.find()) {
            final String paramName = m.group(1);
            final ObjectNode param = objectMapper.createObjectNode();
            param.put("name", paramName);
            param.put("in", "path");
            param.put("required", true);
            param.putObject("schema").put("type", "string");
            if (metadata != null && metadata.parameterDescriptions().containsKey(paramName)) {
                param.put("description", metadata.parameterDescriptions().get(paramName));
            }
            params.add(param);
        }
        return params;
    }
}
