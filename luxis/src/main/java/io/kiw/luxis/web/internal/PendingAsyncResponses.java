package io.kiw.luxis.web.internal;

import java.util.function.Consumer;

public class PendingAsyncResponses {
    private final TimeoutScheduler scheduler;
    private final Consumer<Exception> exceptionHandler;

    public PendingAsyncResponses(final TimeoutScheduler scheduler, final Consumer<Exception> exceptionHandler) {
        this.scheduler = scheduler;
        this.exceptionHandler = exceptionHandler;
    }

    public TimeoutScheduler.Cancellable scheduleTimeout(final long delayMillis, final Runnable action, final ScheduleType scheduleType) {
        return scheduler.schedule(scheduleType, delayMillis, action);
    }

    public void reportException(final Exception e) {
        exceptionHandler.accept(e);
    }
}
