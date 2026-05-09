package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class ContextAssertingHttpHandler implements JsonHandler<ContextRequest, ContextResponse, MyApplicationState> {

    private final ContextAsserter asserter;

    public ContextAssertingHttpHandler(final ContextAsserter asserter) {
        this.asserter = asserter;
    }

    @Override
    public LuxisPipeline<ContextResponse> handle(final HttpStream<ContextRequest, MyApplicationState> httpStream) {
        return httpStream
                .blockingMap(ctx -> {
                    asserter.assertInWorkerContext();
                    return ctx.in().message;
                })
                .flatMap(ctx -> {
                    asserter.assertInApplicationContext();
                    return success(ctx.in() + " flatMap");
                })
                .blockingFlatMap(ctx -> {
                    asserter.assertInWorkerContext();
                    return success(ctx.in() + " blockingFlatMap");
                })
                .map(ctx -> {
                    asserter.assertInApplicationContext();
                    return ctx.in() + " map";
                })
                .blockingMap(ctx -> {
                    asserter.assertInWorkerContext();
                    return ctx.in() + " blocking2";
                })
                .complete(ctx -> success(new ContextResponse(ctx.in())));
    }
}
