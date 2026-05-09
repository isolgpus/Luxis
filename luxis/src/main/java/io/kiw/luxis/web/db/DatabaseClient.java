package io.kiw.luxis.web.db;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface DatabaseClient<TX, ROW, KEY> {

    Future<TX> begin();

    Future<Void> commit(TX tx);

    Future<Void> rollback(TX tx);

    default Future<Void> onCommitted(final TX tx, final Runnable callback) {
        callback.run();
        return Future.succeededFuture();
    }

    <T> Future<List<T>> query(TX tx, String sql, Function<ROW, T> rowMapper, Object... params);

    <T> Future<List<T>> query(TX tx, String sql, Function<ROW, T> rowMapper, Map<String, Object> params);

    Future<UpdateResult<KEY>> update(TX tx, String sql, Object... params);

    Future<UpdateResult<KEY>> update(TX tx, String sql, Map<String, Object> params);

    Future<BatchUpdateResult<KEY>> updateBatch(TX tx, String sql, List<Object[]> rows);

    Future<BatchUpdateResult<KEY>> updateBatchNamed(TX tx, String sql, List<Map<String, Object>> rows);
}
