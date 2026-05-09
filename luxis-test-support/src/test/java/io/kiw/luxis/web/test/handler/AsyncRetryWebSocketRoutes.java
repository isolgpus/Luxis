package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.pipeline.AsyncMapConfigBuilder;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncRetryWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final AtomicLong counter;
    private final TestRetryBehaviour testRetryBehaviour;

    public AsyncRetryWebSocketRoutes(final AtomicLong counter, final TestRetryBehaviour testRetryBehaviour) {
        this.counter = counter;
        this.testRetryBehaviour = testRetryBehaviour;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister
                .registerInbound("number", WebSocketNumberRequest.class, s ->
                        s.<Integer>asyncMap(ctx -> {
                                    int attempt = (int) counter.getAndIncrement();
                                    CompletableFuture<Result<ErrorMessageResponse, Integer>> future = new CompletableFuture<>();
                                    switch (testRetryBehaviour.getAction(attempt)) {
                                        case SUCCESS -> future.complete(Result.success(ctx.in().value));
                                        case ERROR ->
                                                future.complete(Result.error(new ErrorMessageResponse("Failed running async")));
                                        case EXCEPTION ->
                                                future.completeExceptionally(new RuntimeException("Async exception on attempt " + attempt));
                                        case TIMEOUT -> {
                                        }
                                    }
                                    return new LuxisAsync<>(future);
                                }, new AsyncMapConfigBuilder().retries(3, 50).build())
                                .map(ctx -> new WebSocketNumberResponse(ctx.in()))
                                .complete());
    }
}
