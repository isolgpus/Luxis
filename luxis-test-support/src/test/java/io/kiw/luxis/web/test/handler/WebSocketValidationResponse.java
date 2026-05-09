package io.kiw.luxis.web.test.handler;

public record WebSocketValidationResponse(String name, String email, int age,
                                          String city) implements TestWebSocketResponse {
}
