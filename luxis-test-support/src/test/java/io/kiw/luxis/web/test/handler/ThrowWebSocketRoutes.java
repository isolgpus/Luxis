package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.websocket.WebSocketResult;

public class ThrowWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private Luxis<?> luxis;

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("throw", WebSocketThrowRequest.class, s ->
                        s.map(ctx -> ctx.in().where)
                                .map(ctx -> {
                                    if ("map".equals(ctx.in())) {
                                        throw new RuntimeException("app error in map");
                                    }
                                    return ctx.in();
                                })
                                .blockingMap(ctx -> {
                                    if ("blocking".equals(ctx.in())) {
                                        throw new RuntimeException("app error in blocking");
                                    }
                                    return ctx.in();
                                })
                                .<String>asyncMap(ctx -> {
                                    if ("asyncMap".equals(ctx.in())) {
                                        throw new RuntimeException("app error in asyncMap");
                                    }
                                    final CorrelatedAsync<String, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in()));
                                    return correlated.async();
                                })
                                .<String>asyncBlockingMap(ctx -> {
                                    if ("asyncBlockingMap".equals(ctx.in())) {
                                        throw new RuntimeException("app error in asyncBlockingMap");
                                    }
                                    final CorrelatedAsync<String, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in()));
                                    return correlated.async();
                                })
                                .flatMap(ctx -> {
                                    if ("complete".equals(ctx.in())) {
                                        throw new RuntimeException("app error in complete");
                                    }
                                    return WebSocketResult.success(new WebSocketEchoResponse("ok"));
                                })
                                .complete());

    }

    public void evillyReferenceLuxis(Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
