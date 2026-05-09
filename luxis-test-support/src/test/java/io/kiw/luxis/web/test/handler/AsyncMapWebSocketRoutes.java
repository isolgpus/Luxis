package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class AsyncMapWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private Luxis<?> luxis;

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister
                .registerInbound("number", WebSocketNumberRequest.class, s ->
                        s.asyncMap(ctx -> {
                                    final CorrelatedAsync<Integer, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), Result.success(ctx.in().value * 10));
                                    return correlated.async();
                                })
                                .map(ctx -> new WebSocketNumberResponse(ctx.in()))
                                .complete());

    }

    public void evillyReferenceLuxis(Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
