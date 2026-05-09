package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class TimeoutTestHandler implements JsonHandler<ThrowRequest, ThrowResponse, MyApplicationState> {
    @Override
    public LuxisPipeline<ThrowResponse> handle(final HttpStream<ThrowRequest, MyApplicationState> e) {
        return e
                .blockingMap(ctx -> {
                    LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
                    return ctx.in();

                }).complete(ctx ->
                        HttpResult.error(ErrorStatusCode.INTERNAL_SERVER_ERROR, new ErrorMessageResponse("should not have got here")));
    }
}
