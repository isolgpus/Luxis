package io.kiw.luxis.web.websocket;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorMessageResponse;

public abstract class WebSocketResult {

    public static <S> Result<ErrorMessageResponse, S> from(final Result<String, S> result) {
        return result.fold(
                e -> Result.error(new ErrorMessageResponse(e)),
                Result::success);
    }

    public static <S> Result<ErrorMessageResponse, S> error(final ErrorMessageResponse messageResponse) {
        return Result.error(messageResponse);
    }

    public static <S> Result<ErrorMessageResponse, S> error(final String message) {
        return Result.error(new ErrorMessageResponse(message));
    }

    public static <S> Result<ErrorMessageResponse, S> success(final S response) {
        return Result.success(response);
    }

    public static <S> Result<ErrorMessageResponse, S> success() {
        return Result.success(null);
    }
}
