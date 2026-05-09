package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class NoResponseWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    public boolean messageReceived = false;

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.map(ctx -> {
                                    messageReceived = true;
                                    return ctx.in().message;
                                })
                                .completeWithNoResponse());

    }
}
