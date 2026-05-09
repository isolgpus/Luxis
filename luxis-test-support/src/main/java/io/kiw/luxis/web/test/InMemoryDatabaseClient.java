package io.kiw.luxis.web.test;

import io.kiw.luxis.web.db.BatchUpdateResult;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.db.UpdateResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class InMemoryDatabaseClient implements DatabaseClient<Integer, Map<String, Object>, Long> {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "luxis-db-test-delay");
        thread.setDaemon(true);
        return thread;
    });

    private static final long DELAY_MS = 50L;

    private final AtomicInteger nextTxId = new AtomicInteger(1);
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final Deque<List<Map<String, Object>>> queryResults = new ArrayDeque<>();
    private final Deque<UpdateResult<Long>> updateResults = new ArrayDeque<>();
    private final Deque<BatchUpdateResult<Long>> batchResults = new ArrayDeque<>();

    private boolean commitShouldFail;
    private boolean rollbackShouldFail;

    public InMemoryDatabaseClient failCommits() {
        this.commitShouldFail = true;
        return this;
    }

    public InMemoryDatabaseClient failRollbacks() {
        this.rollbackShouldFail = true;
        return this;
    }

    public InMemoryDatabaseClient enqueueQueryResult(final List<Map<String, Object>> rows) {
        queryResults.add(rows);
        return this;
    }

    public InMemoryDatabaseClient enqueueUpdateResult(final int rowCount, final Long generatedKey) {
        updateResults.add(new UpdateResult<>(rowCount, generatedKey));
        return this;
    }

    public InMemoryDatabaseClient enqueueBatchResult(final int[] rowCounts, final List<Long> generatedKeys) {
        batchResults.add(new BatchUpdateResult<>(rowCounts, generatedKeys));
        return this;
    }

    public List<String> events() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    @Override
    public Future<Integer> begin() {
        final int id = nextTxId.getAndIncrement();
        events.add("begin:" + id);
        return scheduleSuccess(id);
    }

    @Override
    public Future<Void> commit(final Integer tx) {
        events.add("commit:" + tx);
        if (commitShouldFail) {
            return scheduleFailure(new RuntimeException("commit failed"));
        }
        return scheduleSuccess(null);
    }

    @Override
    public Future<Void> rollback(final Integer tx) {
        events.add("rollback:" + tx);
        if (rollbackShouldFail) {
            return scheduleFailure(new RuntimeException("rollback failed"));
        }
        return scheduleSuccess(null);
    }

    @Override
    public Future<Void> onCommitted(final Integer tx, final Runnable callback) {
        events.add("onCommitted:" + tx);
        final Promise<Void> promise = Promise.promise();
        SCHEDULER.schedule(() -> {
            try {
                callback.run();
                promise.complete();
            } catch (final Exception t) {
                promise.fail(t);
            }
        }, DELAY_MS, TimeUnit.MILLISECONDS);
        return promise.future();
    }

    @Override
    public <T> Future<List<T>> query(final Integer tx, final String sql, final Function<Map<String, Object>, T> rowMapper, final Object... params) {
        events.add("query:" + tx + ":" + sql);
        return scheduleSuccess(mapRows(rowMapper));
    }

    @Override
    public <T> Future<List<T>> query(final Integer tx, final String sql, final Function<Map<String, Object>, T> rowMapper, final Map<String, Object> params) {
        events.add("query:" + tx + ":" + sql);
        return scheduleSuccess(mapRows(rowMapper));
    }

    @Override
    public Future<UpdateResult<Long>> update(final Integer tx, final String sql, final Object... params) {
        events.add("update:" + tx + ":" + sql);
        final UpdateResult<Long> next = updateResults.isEmpty() ? new UpdateResult<>(1, null) : updateResults.poll();
        return scheduleSuccess(next);
    }

    @Override
    public Future<UpdateResult<Long>> update(final Integer tx, final String sql, final Map<String, Object> params) {
        events.add("update:" + tx + ":" + sql);
        final UpdateResult<Long> next = updateResults.isEmpty() ? new UpdateResult<>(1, null) : updateResults.poll();
        return scheduleSuccess(next);
    }

    @Override
    public Future<BatchUpdateResult<Long>> updateBatch(final Integer tx, final String sql, final List<Object[]> rows) {
        events.add("updateBatch:" + tx + ":" + sql);
        final BatchUpdateResult<Long> next = batchResults.isEmpty()
                ? new BatchUpdateResult<>(new int[rows.size()], List.of())
                : batchResults.poll();
        return scheduleSuccess(next);
    }

    @Override
    public Future<BatchUpdateResult<Long>> updateBatchNamed(final Integer tx, final String sql, final List<Map<String, Object>> rows) {
        events.add("updateBatchNamed:" + tx + ":" + sql);
        final BatchUpdateResult<Long> next = batchResults.isEmpty()
                ? new BatchUpdateResult<>(new int[rows.size()], List.of())
                : batchResults.poll();
        return scheduleSuccess(next);
    }

    private <T> List<T> mapRows(final Function<Map<String, Object>, T> rowMapper) {
        final List<Map<String, Object>> rows = queryResults.isEmpty() ? List.of() : queryResults.poll();
        final List<T> mapped = new ArrayList<>(rows.size());
        for (final Map<String, Object> row : rows) {
            mapped.add(rowMapper.apply(row));
        }
        return mapped;
    }

    private static <T> Future<T> scheduleSuccess(final T value) {
        final Promise<T> promise = Promise.promise();
        SCHEDULER.schedule(() -> promise.complete(value), DELAY_MS, TimeUnit.MILLISECONDS);
        return promise.future();
    }

    private static <T> Future<T> scheduleFailure(final Throwable cause) {
        final Promise<T> promise = Promise.promise();
        SCHEDULER.schedule(() -> promise.fail(cause), DELAY_MS, TimeUnit.MILLISECONDS);
        return promise.future();
    }
}
