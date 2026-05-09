package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class ErrorHandler implements JsonHandler<Void, Object, MyApplicationState> {

    private final ErrorStatusCode statusCode;
    private final String message;

    public ErrorHandler(final ErrorStatusCode statusCode, final String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    @Override
    public LuxisPipeline<Object> handle(final HttpStream<Void, MyApplicationState> httpStream) {
        return httpStream
                .complete(ctx -> HttpResult.error(statusCode, new ErrorMessageResponse(message)));
    }
}
