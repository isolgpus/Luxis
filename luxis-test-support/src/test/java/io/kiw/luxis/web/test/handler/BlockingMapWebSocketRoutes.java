package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class BlockingMapWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister
                .registerInbound("number", WebSocketNumberRequest.class, s ->
                        s.map(ctx -> ctx.in().value)
                                .blockingMap(ctx -> ctx.in() * 2)
                                .map(ctx -> new WebSocketNumberResponse(ctx.in()))
                                .complete());

    }
}
