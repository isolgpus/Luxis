package io.kiw.luxis.web.pipeline;

public sealed interface CorruptWebSocketInputStrategy permits DisconnectSession, SendErrorResponse {
}
