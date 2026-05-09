package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.AsyncMapConfigBuilder;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncRetryTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    private final AtomicLong counter;
    private final TestRetryBehaviour testRetryBehaviour;

    public AsyncRetryTestHandler(final AtomicLong counter, final TestRetryBehaviour testRetryBehaviour) {
        this.counter = counter;
        this.testRetryBehaviour = testRetryBehaviour;
    }

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Integer>asyncMap(ctx -> {
                    int attempt = (int) counter.getAndIncrement();
                    CompletableFuture<Result<HttpErrorResponse, Integer>> future = new CompletableFuture<>();
                    switch (testRetryBehaviour.getAction(attempt)) {
                        case SUCCESS -> future.complete(Result.success(ctx.in().value));
                        case ERROR ->
                                future.complete(Result.error(new HttpErrorResponse(new ErrorMessageResponse("Failed running async"), 500)));
                        case EXCEPTION ->
                                future.completeExceptionally(new RuntimeException("Async exception on attempt " + attempt));
                        case TIMEOUT -> {
                        }
                    }
                    return new LuxisAsync<>(future);
                }, new AsyncMapConfigBuilder().retries(3, 50).build())
                .map(ctx -> new AsyncMapResponse(ctx.in()))
                .complete(ctx -> HttpResult.success(ctx.in()));
    }

}
