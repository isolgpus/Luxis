package io.kiw.luxis.web.pipeline;

public record SendErrorResponse(String message) implements CorruptWebSocketInputStrategy {
}
