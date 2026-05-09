package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;

import java.util.function.Function;

public final class RestrictedBlockingAsyncRouteContext<IN, ERR> extends RestrictedBlockingRouteContext<IN> {

    private final PendingAsyncResponses pendingAsyncResponses;
    private final Function<HttpErrorResponse, ERR> errorMapper;

    public RestrictedBlockingAsyncRouteContext(final IN in, final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper) {
        super(in);
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.errorMapper = errorMapper;
    }

    public <T> CorrelatedAsync<T, ERR> correlated() {
        return CorrelatedUtil.correlated(pendingAsyncResponses, errorMapper);
    }
}
