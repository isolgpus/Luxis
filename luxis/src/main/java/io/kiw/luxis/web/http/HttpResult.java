package io.kiw.luxis.web.http;

import io.kiw.luxis.result.Result;

public abstract class HttpResult {


    public static <S> Result<HttpErrorResponse, S> from(final Result<String, S> result, final ErrorStatusCode statusCodeOnFailure) {
        return result.fold(
                e -> Result.error(new HttpErrorResponse(new ErrorMessageResponse(e), statusCodeOnFailure)),
                Result::success);
    }

    public static <S> Result<HttpErrorResponse, S> from(final Result<String, S> result) {
        return from(result, ErrorStatusCode.BAD_REQUEST);
    }

    public static <S> Result<HttpErrorResponse, S> error(final ErrorStatusCode statusCode, final ErrorMessageResponse messageResponse) {
        return Result.error(new HttpErrorResponse(messageResponse, statusCode));
    }

    public static <S> Result<HttpErrorResponse, S> success(final S response) {
        return Result.success(response);
    }

    public static <S> Result<HttpErrorResponse, HttpSuccessResponse<S>> success(final S response, final SuccessStatusCode statusCode) {
        return Result.success(new HttpSuccessResponse<>(response, statusCode));
    }

    public static <S> Result<HttpErrorResponse, S> success() {
        return Result.success(null);
    }
}
