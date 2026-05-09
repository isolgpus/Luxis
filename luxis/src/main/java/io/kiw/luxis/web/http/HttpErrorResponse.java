package io.kiw.luxis.web.http;

public record HttpErrorResponse(ErrorMessageResponse errorMessageValue, int statusCode) {

    public HttpErrorResponse(final ErrorMessageResponse errorMessageValue, final ErrorStatusCode statusCode) {
        this(errorMessageValue, statusCode.code());
    }
}
