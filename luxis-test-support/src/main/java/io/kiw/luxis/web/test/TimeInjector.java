package io.kiw.luxis.web.test;

import io.kiw.luxis.web.internal.ScheduleType;

import java.util.function.Consumer;

public class TimeInjector {
    private boolean advance = true;

    public void checkToAdvanceTime(final ScheduleType scheduleType, final long delayMillis, final Consumer<Long> o) {
        if (advance && scheduleType == ScheduleType.RETRY) {
            o.accept(delayMillis);
        }
    }

    public void disableAutomaticallyAdvancingTimeOnRetryAttempts() {
        this.advance = false;
    }
}
