package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class ContextAssertingPeekWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;

    public ContextAssertingPeekWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.map(ctx -> ctx.in().message)
                                .blockingMap(ctx -> {
                                    asserter.assertInWorkerContext();
                                    return ctx.in();
                                })
                                .peek(ctx -> {
                                    asserter.assertInApplicationContext();
                                })
                                .blockingPeek(ctx -> {
                                    asserter.assertInWorkerContext();
                                })
                                .map(ctx -> {
                                    asserter.assertInApplicationContext();
                                    return new WebSocketEchoResponse(ctx.in() + " afterPeek");
                                })
                                .complete());
    }
}
