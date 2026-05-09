package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class GetTestFilterHandler implements JsonHandler<Void, TestFilterResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<TestFilterResponse> handle(final HttpStream<Void, MyApplicationState> e) {
        return e.complete(ctx ->
                HttpResult.success(new TestFilterResponse("hit handler")));
    }
}
