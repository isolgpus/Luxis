package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketResult;

public class ContextAssertingBlockingCompleteWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;

    public ContextAssertingBlockingCompleteWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.map(ctx -> ctx.in().message)
                                .blockingComplete(ctx -> {
                                    asserter.assertInWorkerContext();
                                    return WebSocketResult.success(new WebSocketEchoResponse("echo: " + ctx.in()));
                                }));
    }
}