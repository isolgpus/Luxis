package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.LuxisStream;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketSession;

public class SplitWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private static LuxisPipeline<WebSocketEchoResponse> handleEcho(LuxisStream<WebSocketEchoRequest, MyApplicationState, TestWebSocketResponse, ErrorMessageResponse, WebSocketSession<TestWebSocketResponse>> stream) {
        return stream
                .map(ctx -> new WebSocketEchoResponse("echo: " + ctx.in().message))
                .complete();
    }

    private static LuxisPipeline<WebSocketNumberResponse> handleNumber(LuxisStream<WebSocketNumberRequest, MyApplicationState, TestWebSocketResponse, ErrorMessageResponse, WebSocketSession<TestWebSocketResponse>> stream) {
        return stream.map(ctx -> new WebSocketNumberResponse(ctx.in().value * 2))
                .complete();
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister.registerInbound("echo", WebSocketEchoRequest.class, SplitWebSocketRoutes::handleEcho);
        routesRegister.registerInbound("number", WebSocketNumberRequest.class, SplitWebSocketRoutes::handleNumber);

    }
}
