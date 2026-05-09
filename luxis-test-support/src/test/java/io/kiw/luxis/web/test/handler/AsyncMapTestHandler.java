package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.function.Function;

import static io.kiw.luxis.web.http.HttpResult.success;

public class AsyncMapTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    private final Function<Integer, Result<HttpErrorResponse, Integer>> responder;
    private Luxis<?> luxis;

    public AsyncMapTestHandler() {
        this(value -> Result.success(value * 10));
    }

    public AsyncMapTestHandler(final Function<Integer, Result<HttpErrorResponse, Integer>> responder) {
        this.responder = responder;
    }

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Integer>asyncMap(ctx -> {
                    final CorrelatedAsync<Integer, HttpErrorResponse> correlated = ctx.correlated();
                    luxis.handleAsyncResponse(correlated.correlationId(), responder.apply(ctx.in().value));
                    return correlated.async();
                })
                .map(ctx -> new AsyncMapResponse(ctx.in()))
                .complete(ctx -> success(ctx.in()));
    }

    public void evillyReferenceLuxis(final Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
