package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.CompositeLuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.Map;

public class CompositeAsyncErrorTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Map<String, Object>>asyncMap(ctx -> CompositeLuxisAsync.<HttpErrorResponse>create()
                        .add("ok", AsyncTestSupport.<Integer, HttpErrorResponse>completed(ctx.in().value))
                        .add("bad", AsyncTestSupport.<Integer, HttpErrorResponse>failed(
                                new HttpErrorResponse(new ErrorMessageResponse("composite failure"), 500)))
                        .combine())
                .map(ctx -> new AsyncMapResponse((Integer) ctx.in().get("ok")))
                .complete(ctx -> HttpResult.success(ctx.in()));
    }
}
