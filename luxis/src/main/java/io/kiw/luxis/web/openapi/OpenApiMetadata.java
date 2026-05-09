package io.kiw.luxis.web.openapi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record OpenApiMetadata(String summary, String description, List<String> tags,
                              Map<String, String> parameterDescriptions,
                              String responseDescription, boolean hidden) {

    public OpenApiMetadata {
        tags = Collections.unmodifiableList(tags);
        parameterDescriptions = Collections.unmodifiableMap(parameterDescriptions);
    }
}
