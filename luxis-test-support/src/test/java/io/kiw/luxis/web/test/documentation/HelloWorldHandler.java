package io.kiw.luxis.web.test.documentation;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;

public class HelloWorldHandler implements JsonHandler<HelloWorldRequest, HelloWorldResponse, AppState> {


    @Override
    public LuxisPipeline<HelloWorldResponse> handle(final HttpStream<HelloWorldRequest, AppState> e) {
        return e.complete(ctx -> HttpResult.success(new HelloWorldResponse()));
    }
}
