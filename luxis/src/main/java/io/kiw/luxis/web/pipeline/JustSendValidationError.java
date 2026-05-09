package io.kiw.luxis.web.pipeline;

public record JustSendValidationError() implements FailedValidationStrategy {
    public static final JustSendValidationError INSTANCE = new JustSendValidationError();
}
