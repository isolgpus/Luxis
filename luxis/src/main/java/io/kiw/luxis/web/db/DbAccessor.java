package io.kiw.luxis.web.db;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.client.LuxisAsync;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class DbAccessor<ROW, KEY, ERR> {

    private final DatabaseClient<Object, ROW, KEY> client;
    private final Object tx;

    @SuppressWarnings("unchecked")
    public DbAccessor(final DatabaseClient<?, ROW, KEY> client, final Object tx) {
        this.client = (DatabaseClient<Object, ROW, KEY>) client;
        this.tx = tx;
    }

    public <T> LuxisAsync<List<T>, ERR> query(final String sql, final Function<ROW, T> rowMapper, final Object... params) {
        return wrap(client.query(tx, sql, rowMapper, params));
    }

    public <T> LuxisAsync<List<T>, ERR> query(final String sql, final Function<ROW, T> rowMapper, final Map<String, Object> params) {
        return wrap(client.query(tx, sql, rowMapper, params));
    }

    public LuxisAsync<UpdateResult<KEY>, ERR> update(final String sql, final Object... params) {
        return wrap(client.update(tx, sql, params));
    }

    public LuxisAsync<UpdateResult<KEY>, ERR> update(final String sql, final Map<String, Object> params) {
        return wrap(client.update(tx, sql, params));
    }

    public LuxisAsync<BatchUpdateResult<KEY>, ERR> updateBatch(final String sql, final List<Object[]> rows) {
        return wrap(client.updateBatch(tx, sql, rows));
    }

    public LuxisAsync<BatchUpdateResult<KEY>, ERR> updateBatchNamed(final String sql, final List<Map<String, Object>> rows) {
        return wrap(client.updateBatchNamed(tx, sql, rows));
    }

    private static <T, ERR> LuxisAsync<T, ERR> wrap(final io.vertx.core.Future<T> future) {
        final CompletableFuture<Result<ERR, T>> cf = new CompletableFuture<>();
        future.onComplete(ar -> {
            if (ar.succeeded()) {
                cf.complete(Result.success(ar.result()));
            } else {
                cf.completeExceptionally(ar.cause());
            }
        });
        return new LuxisAsync<>(cf);
    }
}
