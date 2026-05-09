package io.kiw.luxis.web.test;

import io.kiw.luxis.web.messaging.OutboxEvent;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.PendingOutboxEvent;
import io.vertx.core.Future;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryOutboxStore implements OutboxStore<Integer> {

    private final AtomicLong nextId = new AtomicLong(1);
    private final Deque<PendingOutboxEvent> pending = new ArrayDeque<>();
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    private boolean appendsShouldFail;
    private int batchSize = 100;
    private boolean drainerEnabled = true;

    public InMemoryOutboxStore failAppends() {
        this.appendsShouldFail = true;
        return this;
    }

    public InMemoryOutboxStore withBatchSize(final int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public InMemoryOutboxStore disableDrainer() {
        this.drainerEnabled = false;
        return this;
    }

    public List<String> events() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    @Override
    public Future<Void> append(final Integer tx, final List<OutboxEvent> incoming) {
        events.add("append:" + tx + ":" + incoming.size());
        if (appendsShouldFail) {
            return Future.failedFuture(new RuntimeException("append failed"));
        }
        synchronized (pending) {
            for (final OutboxEvent ev : incoming) {
                pending.add(new PendingOutboxEvent(nextId.getAndIncrement(), ev));
            }
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<List<PendingOutboxEvent>> readPending(final int limit) {
        final List<PendingOutboxEvent> out = new ArrayList<>();
        synchronized (pending) {
            while (out.size() < limit && !pending.isEmpty()) {
                out.add(pending.peekFirst());
                pending.removeFirst();
            }
        }
        if (!out.isEmpty()) {
            events.add("readPending:" + out.size());
        }
        return Future.succeededFuture(out);
    }

    @Override
    public Future<Void> markBatchSent(final List<Long> ids) {
        events.add("markBatchSent:" + ids.size());
        return Future.succeededFuture();
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public long pollIntervalMillis() {
        return 50L;
    }

    @Override
    public boolean drainerEnabled() {
        return drainerEnabled;
    }
}
