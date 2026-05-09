package io.kiw.luxis.web.test.internal;

import io.kiw.luxis.web.internal.ScheduleType;
import io.kiw.luxis.web.internal.TimeoutScheduler;
import io.kiw.luxis.web.test.TimeInjector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StubTimeoutScheduler implements TimeoutScheduler {

    private final List<ScheduledEntry> entries = new ArrayList<>();
    private final TimeInjector timeInjector;

    public StubTimeoutScheduler(final TimeInjector timeInjector) {
        this.timeInjector = timeInjector;
    }

    @Override
    public Cancellable schedule(final ScheduleType scheduleType, final long delayMillis, final Runnable action) {
        final ScheduledEntry entry = new ScheduledEntry(delayMillis, action);
        entries.add(entry);
        timeInjector.checkToAdvanceTime(scheduleType, delayMillis, this::advanceBy);
        return () -> entries.remove(entry);
    }

    public void advanceBy(final long millis) {
        final List<ScheduledEntry> snapshot = new ArrayList<>(entries);
        for (final ScheduledEntry entry : snapshot) {
            entry.remainingMillis -= millis;
        }
        final List<ScheduledEntry> actionsToExecute = new ArrayList<>();

        final Iterator<ScheduledEntry> iterator = entries.iterator();

        while (iterator.hasNext()) {
            final ScheduledEntry entry = iterator.next();
            if (entry.remainingMillis <= 0) {
                iterator.remove();
                actionsToExecute.add(entry);
            }
        }
        try {
            for (final ScheduledEntry scheduledEntry : actionsToExecute) {
                scheduledEntry.action.run();
            }
        } finally {
            actionsToExecute.clear();
        }

    }

    private static final class ScheduledEntry {
        long remainingMillis;
        final Runnable action;

        ScheduledEntry(final long remainingMillis, final Runnable action) {
            this.remainingMillis = remainingMillis;
            this.action = action;
        }
    }
}
