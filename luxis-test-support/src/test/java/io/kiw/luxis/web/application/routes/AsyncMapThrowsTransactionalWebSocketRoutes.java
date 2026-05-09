package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.handler.TestWebSocketResponse;
import io.kiw.luxis.web.test.handler.WebSocketEchoRequest;
import io.kiw.luxis.web.test.handler.WebSocketEchoResponse;

public class AsyncMapThrowsTransactionalWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("echoResponse", WebSocketEchoResponse.class);

        routesRegister.registerInbound("echo", WebSocketEchoRequest.class, s ->
                s.map(ctx -> ctx.in().message)
                        .inTransaction(txStream -> txStream
                                .<String>asyncMap(ctx -> AsyncTestSupport.failed(new RuntimeException("async driver failed")))
                                .onCompletion(ctx -> ctx.app().setLongValue(99))
                                .commit())
                        .map(ctx -> new WebSocketEchoResponse(ctx.in()))
                        .complete());
    }

}
