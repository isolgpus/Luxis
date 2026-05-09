package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

public class TransactionalHttpHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {

    private final ContextAsserter asserter;

    public TransactionalHttpHandler(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
        return e.map(ctx -> {
                    asserter.notInTransaction();
                    return ctx.in().stringExample;
                })
                .asyncMap(ctx -> AsyncTestSupport.completed(ctx.in() + "-updated"))
                .inTransaction(txStream -> txStream
                        .asyncPeek(ctx -> {
                            asserter.inTransaction();
                            return ctx.db().query("select * from users where username = ? FOR UPDATE", row -> null, ctx.in())
                                    .map(a -> a);
                        })
                        .peek(ctx -> asserter.inTransaction())
                        .asyncPeek(ctx -> {
                            asserter.inTransaction();
                            return ctx.db().update("insert into users values (?)", ctx.in());
                        })
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
