package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class AsyncWithHttpContextTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {


    private Luxis<?> luxis;

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .map(ctx -> {
                    final String multiplier = ctx.session().getQueryParam("multiplier");
                    return multiplier != null ? Integer.parseInt(multiplier) : 1;
                })
                .<Integer>asyncMap(ctx -> {
                    final CorrelatedAsync<Integer, HttpErrorResponse> correlated = ctx.correlated();
                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in() * 7));
                    return correlated.async();
                })
                .map(ctx -> new AsyncMapResponse(ctx.in()))
                .complete(ctx -> success(ctx.in()));
    }

    public void evillyReferenceLuxis(Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
