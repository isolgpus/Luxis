package io.kiw.luxis.web.messaging;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.OutboxDrainer;
import io.vertx.core.Future;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AsyncPublisher<ERR> {

    private final OutboxStore<?> outboxStore;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final OutboxDrainer drainer;

    public AsyncPublisher(final OutboxStore<?> outboxStore, final DatabaseClient<?, ?, ?> databaseClient, final OutboxDrainer drainer) {
        this.outboxStore = outboxStore;
        this.databaseClient = databaseClient;
        this.drainer = drainer;
    }

    public LuxisAsync<Void, ERR> publish(final String key, final String message) {
        return dispatch(OutboxEvent.of(key, message));
    }

    public LuxisAsync<Void, ERR> publish(final String key, final byte[] message) {
        return dispatch(OutboxEvent.of(key, message));
    }

    public LuxisAsync<Void, ERR> publish(final String key, final ByteBuffer message) {
        return dispatch(OutboxEvent.of(key, message));
    }

    private LuxisAsync<Void, ERR> dispatch(final OutboxEvent event) {
        if (outboxStore == null || databaseClient == null) {
            throw new IllegalStateException(
                    "Cannot publish — no Publisher registered at Luxis.app(...).");
        }
        return wrap(appendInOwnTx(event));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Future<Void> appendInOwnTx(final OutboxEvent event) {
        final DatabaseClient db = databaseClient;
        final OutboxStore store = outboxStore;
        return db.begin().compose(tx -> store.append(tx, List.of(event))
                .compose(v -> db.commit(tx))
                .onFailure(err -> db.rollback(tx))
                .onSuccess(v -> {
                    if (drainer != null) {
                        drainer.kick();
                    }
                }));
    }

    private static <ERR> LuxisAsync<Void, ERR> wrap(final Future<Void> future) {
        final CompletableFuture<Result<ERR, Void>> cf = new CompletableFuture<>();
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
