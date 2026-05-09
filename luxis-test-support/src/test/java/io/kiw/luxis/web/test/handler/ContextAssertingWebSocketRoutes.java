package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class ContextAssertingWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;

    public ContextAssertingWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.map(ctx -> {
                                    asserter.assertInApplicationContext();
                                    return ctx.in().message;
                                })
                                .blockingMap(ctx -> {
                                    asserter.assertInWorkerContext();
                                    return ctx.in() + " blocked";
                                })
                                .map(ctx -> {
                                    asserter.assertInApplicationContext();
                                    return new WebSocketEchoResponse(ctx.in());
                                })
                                .complete());

    }
}
