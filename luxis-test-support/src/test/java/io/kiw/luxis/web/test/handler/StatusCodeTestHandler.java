package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.HttpSuccessResponse;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class StatusCodeTestHandler implements JsonHandler<StatusCodeRequest, HttpSuccessResponse<StatusCodeResponse>, MyApplicationState> {

    @Override
    public LuxisPipeline<HttpSuccessResponse<StatusCodeResponse>> handle(final HttpStream<StatusCodeRequest, MyApplicationState> e) {
        return e.complete(ctx ->
                HttpResult.success(new StatusCodeResponse(ctx.in().value), ctx.in().statusCode));
    }
}
