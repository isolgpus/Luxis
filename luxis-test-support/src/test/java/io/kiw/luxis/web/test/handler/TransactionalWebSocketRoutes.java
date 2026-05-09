package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class TransactionalWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final ContextAsserter asserter;

    public TransactionalWebSocketRoutes(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister.registerInbound("echo", WebSocketEchoRequest.class, s ->
                s.map(ctx -> {
                            asserter.notInTransaction();
                            return ctx.in().message;
                        })
                        .inTransaction(txStream -> txStream
                                .map(ctx -> {
                                    asserter.inTransaction();
                                    return ctx.in() + "-queried";
                                })
                                .asyncMap(ctx -> {
                                    asserter.inTransaction();
                                    return AsyncTestSupport.completed(ctx.in() + "-updated");
                                })
                                .flatMap(ctx -> {
                                    asserter.inTransaction();
                                    return Result.success(ctx.in());
                                })
                                .onCompletion(ctx -> {
                                    asserter.notInTransaction();
                                    ctx.app().setLongValue(99);
                                })
                                .commit())
                        .map(ctx -> {
                            asserter.notInTransaction();
                            return new WebSocketEchoResponse(ctx.in());
                        })
                        .complete());
    }

}
