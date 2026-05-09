package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.http.ErrorMessageResponse;

public interface ErrorMessageResponseMapper<ERR> {
    ERR map(ErrorMessageResponse errorMessageResponse, ErrorCause cause);
}
