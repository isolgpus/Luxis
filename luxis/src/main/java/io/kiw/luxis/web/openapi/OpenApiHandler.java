package io.kiw.luxis.web.openapi;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class OpenApiHandler implements JsonHandler<Void, ObjectNode, Object> {
    private final OpenApiCollector collector;
    private final ObjectMapper objectMapper;
    private final String title;
    private final String version;
    private final String description;
    private volatile ObjectNode cachedSpec;

    public OpenApiHandler(final OpenApiCollector collector, final ObjectMapper objectMapper,
                          final String title, final String version, final String description) {
        this.collector = collector;
        this.objectMapper = objectMapper;
        this.title = title;
        this.version = version;
        this.description = description;
    }

    @Override
    public LuxisPipeline<ObjectNode> handle(final HttpStream<Void, Object> e) {
        return e.complete(ctx -> {
            if (cachedSpec == null) {
                final OpenApiSpecGenerator generator = new OpenApiSpecGenerator(collector, objectMapper);
                generator.title(title).version(version).description(description);
                cachedSpec = generator.generate();
            }
            return HttpResult.success(cachedSpec);
        });
    }
}
