package io.kiw.luxis.web.pipeline;

public record SendValidationErrorsAndDisconnectSession() implements FailedValidationStrategy {
    public static final SendValidationErrorsAndDisconnectSession INSTANCE = new SendValidationErrorsAndDisconnectSession();
}
