package io.kiw.luxis.web.internal;

import io.vertx.core.Vertx;

public class VertxTimeoutScheduler implements TimeoutScheduler {
    private final Vertx vertx;

    public VertxTimeoutScheduler(final Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Cancellable schedule(final ScheduleType scheduleType, final long delayMillis, final Runnable action) {
        final long timerId = vertx.setTimer(delayMillis, id -> action.run());
        return () -> vertx.cancelTimer(timerId);
    }
}
