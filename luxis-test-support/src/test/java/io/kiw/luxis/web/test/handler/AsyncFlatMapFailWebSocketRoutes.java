package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.MyApplicationState;

public class AsyncFlatMapFailWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private Luxis<?> luxis;

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister
                .registerInbound("echo", WebSocketEchoRequest.class, s ->
                        s.<WebSocketEchoResponse>asyncMap(ctx -> {
                                    final CorrelatedAsync<WebSocketEchoResponse, ErrorMessageResponse> correlated = ctx.correlated();
                                    luxis.handleAsyncResponse(correlated.correlationId(), HttpResult.error(ErrorStatusCode.BAD_REQUEST, new ErrorMessageResponse("async flatMap failed")));
                                    return correlated.async();
                                })
                                .complete());
    }

    public void evillyReferenceLuxis(Luxis<?> luxis) {
        this.luxis = luxis;
    }
}
