package io.kiw.luxis.web.openapi;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JsonSchemaGenerator {
    private final ObjectMapper objectMapper;

    public JsonSchemaGenerator(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generateSchema(final Type type) {
        if (type == null || type == Void.class || type == void.class) {
            return null;
        }
        return generateSchemaForType(type);
    }

    private ObjectNode generateSchemaForType(final Type type) {
        if (type instanceof Class<?> cls) {
            return generateSchemaForClass(cls);
        } else if (type instanceof ParameterizedType pt) {
            return generateSchemaForParameterized(pt);
        }
        return objectMapper.createObjectNode().put("type", "object");
    }

    private ObjectNode generateSchemaForClass(final Class<?> cls) {
        if (cls == int.class || cls == Integer.class) {
            return objectMapper.createObjectNode().put("type", "integer");
        }
        if (cls == long.class || cls == Long.class) {
            return objectMapper.createObjectNode().put("type", "integer").put("format", "int64");
        }
        if (cls == double.class || cls == Double.class) {
            return objectMapper.createObjectNode().put("type", "number").put("format", "double");
        }
        if (cls == float.class || cls == Float.class) {
            return objectMapper.createObjectNode().put("type", "number").put("format", "float");
        }
        if (cls == boolean.class || cls == Boolean.class) {
            return objectMapper.createObjectNode().put("type", "boolean");
        }
        if (cls == String.class) {
            return objectMapper.createObjectNode().put("type", "string");
        }

        final ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        final ObjectNode properties = schema.putObject("properties");
        final ArrayNode required = objectMapper.createArrayNode();

        if (cls.isRecord()) {
            for (final RecordComponent component : cls.getRecordComponents()) {
                properties.set(component.getName(), generateSchemaForType(component.getGenericType()));
                if (component.getType().isPrimitive()) {
                    required.add(component.getName());
                }
            }
        } else {
            for (final Field field : cls.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                properties.set(field.getName(), generateSchemaForType(field.getGenericType()));
                if (field.getType().isPrimitive()) {
                    required.add(field.getName());
                }
            }
        }
        if (required.size() > 0) {
            schema.set("required", required);
        }
        return schema;
    }

    private ObjectNode generateSchemaForParameterized(final ParameterizedType pt) {
        final Class<?> raw = (Class<?>) pt.getRawType();
        if (List.class.isAssignableFrom(raw) || Collection.class.isAssignableFrom(raw)) {
            final ObjectNode schema = objectMapper.createObjectNode().put("type", "array");
            schema.set("items", generateSchemaForType(pt.getActualTypeArguments()[0]));
            return schema;
        }
        if (Map.class.isAssignableFrom(raw)) {
            final ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
            schema.set("additionalProperties", generateSchemaForType(pt.getActualTypeArguments()[1]));
            return schema;
        }
        return generateSchemaForClass(raw);
    }
}
