package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.CompositeLuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.Map;

public class CompositeAsyncTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Map<String, Object>>asyncMap(ctx -> {
                    final int value = ctx.in().value;
                    return CompositeLuxisAsync.<HttpErrorResponse>create()
                            .add("doubled", AsyncTestSupport.<Integer, HttpErrorResponse>completed(value * 2))
                            .add("tripled", AsyncTestSupport.<Integer, HttpErrorResponse>completed(value * 3))
                            .combine();
                })
                .map(ctx -> {
                    final Map<String, Object> results = ctx.in();
                    final int doubled = (Integer) results.get("doubled");
                    final int tripled = (Integer) results.get("tripled");
                    return new AsyncMapResponse(doubled + tripled);
                })
                .complete(ctx -> HttpResult.success(ctx.in()));
    }
}
