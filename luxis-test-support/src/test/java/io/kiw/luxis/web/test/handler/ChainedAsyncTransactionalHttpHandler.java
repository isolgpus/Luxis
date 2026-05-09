package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class ChainedAsyncTransactionalHttpHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {

    private final ContextAsserter asserter;

    public ChainedAsyncTransactionalHttpHandler(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
        return e.map(ctx -> {
                    asserter.notInTransaction();
                    return ctx.in().stringExample;
                })
                .inTransaction(tx -> tx
                        .asyncMap(ctx -> {
                            asserter.inTransaction();
                            return AsyncTestSupport.<String, HttpErrorResponse>completed(ctx.in() + "-first");
                        })
                        .map(ctx -> {
                            asserter.inTransaction();
                            return ctx.in() + "-between";
                        })
                        .asyncMap(ctx -> {
                            asserter.inTransaction();
                            return AsyncTestSupport.<String, HttpErrorResponse>completed(ctx.in() + "-second");
                        })
                        .peek(ctx -> asserter.inTransaction())
                        .onCompletion(ctx -> {
                            asserter.notInTransaction();
                            ctx.app().setLongValue(99);
                        })
                        .commit())
                .complete(ctx -> {
                    asserter.notInTransaction();
                    return HttpResult.success(new EchoResponse(0, ctx.in(), null, null, null, null));
                });
    }
}
