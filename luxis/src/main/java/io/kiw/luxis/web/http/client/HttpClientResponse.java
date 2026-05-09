package io.kiw.luxis.web.http.client;

import java.util.Map;

public record HttpClientResponse<T>(int statusCode, T body, Map<String, String> headers) {
}
