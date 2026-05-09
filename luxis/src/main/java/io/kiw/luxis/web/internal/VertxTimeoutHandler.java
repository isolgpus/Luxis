package io.kiw.luxis.web.internal;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class VertxTimeoutHandler implements Handler<RoutingContext> {

    private final int timeout;

    public VertxTimeoutHandler(final int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        final long tid = ctx.vertx().setTimer(timeout, (t) -> {
            ctx.data().put("CONTEXT_DEAD", true);
            ctx.end("{\"message\":\"Service Unavailable\"}");
        });

        ctx.addBodyEndHandler((v) -> {
            ctx.vertx().cancelTimer(tid);
        });
        ctx.next();
    }
}
