package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonFilter;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class ErrorFilter implements JsonFilter<MyApplicationState> {

    @Override
    public LuxisPipeline<Void> handle(final HttpStream<Void, MyApplicationState> e) {
        return e.complete(ctx -> HttpResult.error(ErrorStatusCode.UNAUTHORIZED, new ErrorMessageResponse("filter blocked")));
    }
}
