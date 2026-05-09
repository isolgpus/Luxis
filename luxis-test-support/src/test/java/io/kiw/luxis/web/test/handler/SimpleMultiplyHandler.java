package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class SimpleMultiplyHandler implements JsonHandler<SimpleValueRequest, SimpleValueResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<SimpleValueResponse> handle(final HttpStream<SimpleValueRequest, MyApplicationState> httpStream) {
        return httpStream
                .map(ctx -> ctx.in().value * 10)
                .complete(ctx -> success(new SimpleValueResponse(ctx.in())));
    }
}
