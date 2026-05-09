package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class ThrowTestHandler implements JsonHandler<ThrowRequest, ThrowResponse, MyApplicationState> {
    @Override
    public LuxisPipeline<ThrowResponse> handle(final HttpStream<ThrowRequest, MyApplicationState> e) {
        return e.map(ctx -> ctx.in().where).map(ctx -> {
            if ("map".equals(ctx.in())) {
                throw new RuntimeException("app error in map");
            }
            return ctx.in();
        }).blockingMap(ctx -> {
            if ("blocking".equals(ctx.in())) {
                throw new RuntimeException("app error in blocking");
            }
            return ctx.in();
        }).complete(ctx -> {
            if ("complete".equals(ctx.in())) {
                throw new RuntimeException("app error in complete");
            }
            return HttpResult.success(new ThrowResponse());
        });
    }
}
