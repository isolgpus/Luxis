package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.EchoResponse;

public class AsyncMapTypedErrorTransactionalHttpHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
        return e.map(ctx -> ctx.in().stringExample)
                .inTransaction(tx -> tx
                        .<String>asyncMap(ctx -> AsyncTestSupport.failed(new HttpErrorResponse(new ErrorMessageResponse("async typed error"), ErrorStatusCode.BAD_REQUEST)))
                        .onCompletion(ctx -> ctx.app().setLongValue(99))
                        .commit())
                .complete(ctx -> HttpResult.success(new EchoResponse(0, ctx.in(), null, null, null, null)));
    }
}
