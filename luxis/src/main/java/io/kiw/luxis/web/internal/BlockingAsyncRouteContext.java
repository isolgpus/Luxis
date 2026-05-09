package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;

import java.util.function.Function;

public final class BlockingAsyncRouteContext<IN, SESSION, ERR> extends BlockingRouteContext<IN, SESSION> {

    private final PendingAsyncResponses pendingAsyncResponses;
    private final Function<HttpErrorResponse, ERR> errorMapper;

    public BlockingAsyncRouteContext(final IN in, final SESSION session, final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper) {
        super(in, session);
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.errorMapper = errorMapper;
    }

    public <T> CorrelatedAsync<T, ERR> correlated() {
        return CorrelatedUtil.correlated(pendingAsyncResponses, errorMapper);
    }
}
