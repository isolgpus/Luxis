package io.kiw.luxis.web.pipeline;

public record DisconnectSession() implements CorruptWebSocketInputStrategy, FailedValidationStrategy {
    public static final DisconnectSession INSTANCE = new DisconnectSession();
}
