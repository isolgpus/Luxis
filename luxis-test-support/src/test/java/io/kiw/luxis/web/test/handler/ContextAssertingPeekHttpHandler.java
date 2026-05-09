package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class ContextAssertingPeekHttpHandler implements JsonHandler<ContextRequest, ContextResponse, MyApplicationState> {

    private final ContextAsserter asserter;

    public ContextAssertingPeekHttpHandler(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public LuxisPipeline<ContextResponse> handle(final HttpStream<ContextRequest, MyApplicationState> httpStream) {
        return httpStream
                .map(ctx -> ctx.in().message)
                .peek(ctx -> {
                    asserter.assertInApplicationContext();
                })
                .blockingPeek(ctx -> {
                    asserter.assertInWorkerContext();
                })
                .map(ctx -> {
                    asserter.assertInApplicationContext();
                    return ctx.in() + " afterPeek";
                })
                .complete(ctx -> success(new ContextResponse(ctx.in())));
    }
}
