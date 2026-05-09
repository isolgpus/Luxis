package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class SimpleGetHandler implements JsonHandler<Void, SimpleValueResponse, MyApplicationState> {

    private final int valueToReturn;

    public SimpleGetHandler(final int valueToReturn) {
        this.valueToReturn = valueToReturn;
    }

    @Override
    public LuxisPipeline<SimpleValueResponse> handle(final HttpStream<Void, MyApplicationState> httpStream) {
        return httpStream
                .complete(ctx -> success(new SimpleValueResponse(valueToReturn)));
    }
}
