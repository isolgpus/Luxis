package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketResult;

public class FlatMapFailWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.<WebSocketEchoResponse>flatMap(ctx -> WebSocketResult.error(new ErrorMessageResponse("flatMap failed")))
                                .complete());

    }
}
