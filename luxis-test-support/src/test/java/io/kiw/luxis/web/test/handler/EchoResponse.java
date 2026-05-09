package io.kiw.luxis.web.test.handler;

public record EchoResponse(int intExample, String stringExample, String pathExample,
                           String queryExample, String requestHeaderExample,
                           String requestCookieExample) {
}
