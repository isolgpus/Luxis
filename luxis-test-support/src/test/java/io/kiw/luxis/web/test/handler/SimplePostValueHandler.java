package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.application.routes.ValueRequest;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class SimplePostValueHandler implements JsonHandler<ValueRequest, SimpleStringValueResponse, MyApplicationState> {


    public SimplePostValueHandler() {
    }

    @Override
    public LuxisPipeline<SimpleStringValueResponse> handle(final HttpStream<ValueRequest, MyApplicationState> httpStream) {
        return httpStream
                .complete(ctx -> success(new SimpleStringValueResponse(ctx.in().stringValue() + " string")));
    }
}
