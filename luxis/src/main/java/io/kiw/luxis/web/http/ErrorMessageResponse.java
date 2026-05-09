package io.kiw.luxis.web.http;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ErrorMessageResponse(String message, Map<String, List<String>> errors) {

    public ErrorMessageResponse(final String message) {
        this(message, Collections.emptyMap());
    }
}
