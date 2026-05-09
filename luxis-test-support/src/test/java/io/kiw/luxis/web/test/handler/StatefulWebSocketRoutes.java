package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketSession;

public class StatefulWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void onOpen(final WebSocketSession<TestWebSocketResponse> session, final MyApplicationState appState) {
        session.send(new WebSocketEchoResponse("connected"));
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister.registerInbound("echo", WebSocketEchoRequest.class, s ->
                s.map(ctx -> new WebSocketEchoResponse(ctx.in().message))
                        .complete());

    }

    @Override
    public void onClose(final WebSocketSession<TestWebSocketResponse> session, final MyApplicationState appState) {
    }
}
