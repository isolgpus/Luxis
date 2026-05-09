package io.kiw.luxis.web.test.handler;

public class HttpClientGetResponse {
    public int statusCode;
    public String body;

    public HttpClientGetResponse(final int statusCode, final String body) {
        this.statusCode = statusCode;
        this.body = body;
    }
}
