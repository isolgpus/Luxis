package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.db.DbAccessor;
import io.kiw.luxis.web.messaging.AsyncPublisher;
import io.kiw.luxis.web.messaging.OutboxStore;

public final class AsyncRouteContext<IN, APP, SESSION, ERR> extends RouteContext<IN, APP, SESSION> {

    private final DatabaseClient<?, ?, ?> databaseClient;
    private final OutboxStore<?> outboxStore;
    private final OutboxDrainer drainer;


    public AsyncRouteContext(final IN in, final SESSION session, final APP app, final DatabaseClient<?, ?, ?> databaseClient, final OutboxStore<?> outboxStore, final OutboxDrainer drainer) {
        super(in, session, app);
        this.databaseClient = databaseClient;
        this.outboxStore = outboxStore;
        this.drainer = drainer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <ROW, KEY> DbAccessor<ROW, KEY, ERR> db() {
        if (databaseClient == null) {
            return null;
        }
        return new DbAccessor<>((DatabaseClient) databaseClient, null);
    }

    public AsyncPublisher<ERR> publisher() {
        return new AsyncPublisher<>(outboxStore, databaseClient, drainer);
    }
}
