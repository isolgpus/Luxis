package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketSession;

public class OnCloseTrackingSplitWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void onOpen(final WebSocketSession<TestWebSocketResponse> session, final MyApplicationState appState) {

    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, stream ->
                        stream.map(ctx -> new WebSocketEchoResponse("echo: " + ctx.in().message))
                                .complete());

    }

    @Override
    public void onClose(final WebSocketSession<TestWebSocketResponse> session, final MyApplicationState appState) {

    }
}
