package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.handler.TestWebSocketResponse;
import io.kiw.luxis.web.test.handler.WebSocketEchoRequest;
import io.kiw.luxis.web.test.handler.WebSocketEchoResponse;

public class RollbackOnErrorWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;

    public RollbackOnErrorWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister.registerInbound("echo", WebSocketEchoRequest.class, s ->
                s.map(ctx -> ctx.in().message)
                        .inTransaction(txStream -> txStream
                                .<String>flatMap(ctx -> {
                                    asserter.inTransaction();
                                    return Result.error(new ErrorMessageResponse("sub-chain failed"));
                                })
                                .onCompletion(ctx -> ctx.app().setLongValue(99))
                                .commit())
                        .map(ctx -> new WebSocketEchoResponse(ctx.in()))
                        .complete());
    }

}
