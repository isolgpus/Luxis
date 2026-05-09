package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class StateTestHandler implements JsonHandler<Void, StateResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<StateResponse> handle(final HttpStream<Void, MyApplicationState> e) {
        return e.complete(ctx ->
                HttpResult.success(new StateResponse(ctx.app().getLongValue())));
    }
}
