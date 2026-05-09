package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.pipeline.AsyncMapConfigBuilder;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;

public class WebSocketCustomTimeoutRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private Runnable onRegistered = () -> {
    };

    public WebSocketCustomTimeoutRoutes() {
    }

    public void setOnRegistered(final Runnable onRegistered) {
        this.onRegistered = onRegistered;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister
                .registerInbound("number", WebSocketNumberRequest.class, s ->
                        s.<Integer>asyncMap(ctx -> {
                                    // Deliberately do NOT complete — simulates missing response
                                    onRegistered.run();
                                    return new LuxisAsync<>(new CompletableFuture<Result<ErrorMessageResponse, Integer>>());
                                }, new AsyncMapConfigBuilder().setTimeoutMillis(1_000).build())
                                .map(ctx -> new WebSocketNumberResponse(ctx.in()))
                                .complete());

    }
}
