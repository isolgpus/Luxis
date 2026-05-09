package io.kiw.luxis.web.internal;

public interface TimeoutScheduler {
    Cancellable schedule(ScheduleType scheduleType, long delayMillis, Runnable action);

    interface Cancellable {
        void cancel();
    }
}
