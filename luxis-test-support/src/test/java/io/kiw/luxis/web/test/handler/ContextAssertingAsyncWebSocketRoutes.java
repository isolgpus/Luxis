package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class ContextAssertingAsyncWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;
    private Luxis<?> luxis;

    public ContextAssertingAsyncWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.blockingMap(ctx -> {
                                    asserter.assertInWorkerContext();
                                    return ctx.in().message;
                                })
                                .<String>asyncMap(ctx -> {
                                    asserter.assertInApplicationContext();
                                    final CorrelatedAsync<String, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in() + " async"));
                                    return correlated.async();
                                })
                                .map(ctx -> {
                                    asserter.assertInApplicationContext();
                                    return ctx.in() + "map";
                                })
                                .<String>asyncMap(ctx -> {
                                    asserter.assertInApplicationContext();
                                    final CorrelatedAsync<String, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in() + " async2"));
                                    return correlated.async();
                                })
                                .<String>asyncBlockingMap(ctx -> {
                                    asserter.assertInWorkerContext();
                                    final CorrelatedAsync<String, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in() + " async3"));
                                    return correlated.async();
                                })
                                .blockingMap(ctx -> {
                                    asserter.assertInWorkerContext();
                                    return new WebSocketEchoResponse(ctx.in() + " blocking");
                                })
                                .complete());

    }

    public void evillyReferenceLuxis(Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
