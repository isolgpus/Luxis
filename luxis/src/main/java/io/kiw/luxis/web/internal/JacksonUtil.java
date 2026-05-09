package io.kiw.luxis.web.internal;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class JacksonUtil {

    private JacksonUtil() {
    }

    public static ObjectMapper createMapper() {
        return JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }
}
