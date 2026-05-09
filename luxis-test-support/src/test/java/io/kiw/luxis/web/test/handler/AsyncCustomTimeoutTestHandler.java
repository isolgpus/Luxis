package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.AsyncMapConfigBuilder;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;

public class AsyncCustomTimeoutTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    private Runnable onRegistered = () -> {
    };

    public AsyncCustomTimeoutTestHandler() {
    }

    public void setOnRegistered(final Runnable onRegistered) {
        this.onRegistered = onRegistered;
    }

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Integer>asyncMap(ctx -> {
                    // Deliberately do NOT complete — simulates missing response
                    onRegistered.run();
                    return new LuxisAsync<>(new CompletableFuture<Result<HttpErrorResponse, Integer>>());
                }, new AsyncMapConfigBuilder().setTimeoutMillis(200).build())
                .map(ctx -> new AsyncMapResponse(ctx.in()))
                .complete(ctx -> HttpResult.success(ctx.in()));
    }
}
