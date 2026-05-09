package io.kiw.luxis.web.pipeline;

public sealed interface FailedValidationStrategy permits JustSendValidationError, SendValidationErrorsAndDisconnectSession, DisconnectSession {
}
