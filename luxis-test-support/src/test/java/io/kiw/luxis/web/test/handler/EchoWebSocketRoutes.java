package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class EchoWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, stream ->
                        stream.map(ctx -> "echo: " + ctx.in().message)
                                .map(ctx -> new WebSocketEchoResponse(ctx.in()))
                                .complete());

    }
}
