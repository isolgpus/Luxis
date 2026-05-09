package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.atomic.AtomicInteger;

public class PeekWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    public final AtomicInteger peekCount = new AtomicInteger(0);
    public final AtomicInteger blockingPeekCount = new AtomicInteger(0);

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister
                .registerInbound("number", WebSocketNumberRequest.class, s ->
                        s.map(ctx -> ctx.in().value)
                                .peek(ctx -> {
                                    peekCount.incrementAndGet();
                                })
                                .blockingPeek(ctx -> {
                                    blockingPeekCount.incrementAndGet();
                                })
                                .map(ctx -> new WebSocketNumberResponse(ctx.in() * 3))
                                .complete());
    }
}
