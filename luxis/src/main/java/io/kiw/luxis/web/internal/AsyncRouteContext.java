package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.db.DbAccessor;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.messaging.AsyncPublisher;
import io.kiw.luxis.web.messaging.OutboxStore;

import java.util.function.Function;

public final class AsyncRouteContext<IN, APP, SESSION, ERR> extends RouteContext<IN, APP, SESSION> {

    private final PendingAsyncResponses pendingAsyncResponses;
    private final Function<HttpErrorResponse, ERR> errorMapper;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final OutboxStore<?> outboxStore;
    private final OutboxDrainer drainer;

    public AsyncRouteContext(final IN in, final SESSION session, final APP app, final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper) {
        this(in, session, app, pendingAsyncResponses, errorMapper, null, null, null);
    }

    public AsyncRouteContext(final IN in, final SESSION session, final APP app, final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper, final DatabaseClient<?, ?, ?> databaseClient) {
        this(in, session, app, pendingAsyncResponses, errorMapper, databaseClient, null, null);
    }

    public AsyncRouteContext(final IN in, final SESSION session, final APP app, final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper, final DatabaseClient<?, ?, ?> databaseClient, final OutboxStore<?> outboxStore, final OutboxDrainer drainer) {
        super(in, session, app);
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.errorMapper = errorMapper;
        this.databaseClient = databaseClient;
        this.outboxStore = outboxStore;
        this.drainer = drainer;
    }

    public <T> CorrelatedAsync<T, ERR> correlated() {
        return CorrelatedUtil.correlated(pendingAsyncResponses, errorMapper);
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
