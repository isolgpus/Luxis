package io.kiw.luxis.web.http;

public record HttpSuccessResponse<S>(S value, int statusCode) {

    public HttpSuccessResponse(final S value, final SuccessStatusCode statusCode) {
        this(value, statusCode.code());
    }
}
