package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.db.DbAccessor;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.messaging.OutboxEvent;
import io.kiw.luxis.web.messaging.TransactionPublisher;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TransactionAsyncRouteContext<IN, APP, SESSION, ERR> extends TransactionRouteContext<IN, APP, SESSION> {

    private final DatabaseClient<?, ?, ?> databaseClient;
    private final Object tx;
    private final List<OutboxEvent> outboxBuffer;

    public TransactionAsyncRouteContext(final IN in, final APP app, final SESSION session) {
        this(in, app, session, null, null, null);
    }

    public TransactionAsyncRouteContext(final IN in, final APP app, final SESSION session, final DatabaseClient<?, ?, ?> databaseClient, final Object tx) {
        this(in, app, session, databaseClient, tx, null);
    }

    public TransactionAsyncRouteContext(final IN in, final APP app, final SESSION session, final DatabaseClient<?, ?, ?> databaseClient, final Object tx, final List<OutboxEvent> outboxBuffer) {
        super(in, app, session);
        this.databaseClient = databaseClient;
        this.tx = tx;
        this.outboxBuffer = outboxBuffer;
    }

    public <T> LuxisAsync<T, ERR> async() {
        return new LuxisAsync<>(new CompletableFuture<>());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <ROW, KEY> DbAccessor<ROW, KEY, ERR> db() {
        if (databaseClient == null) {
            return null;
        }
        return new DbAccessor<>((DatabaseClient) databaseClient, tx);
    }

    public TransactionPublisher publisher() {
        return new TransactionPublisher(outboxBuffer);
    }
}
