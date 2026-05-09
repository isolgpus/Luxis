package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.client.CorrelatedAsync;
import io.kiw.luxis.web.http.client.LuxisAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class CorrelatedUtil {

    private CorrelatedUtil() {

    }

    public static <T, ERR> CorrelatedAsync<T, ERR> correlated(final PendingAsyncResponses pendingAsyncResponses, final Function<HttpErrorResponse, ERR> errorMapper) {
        final CompletableFuture<Result<HttpErrorResponse, T>> future = new CompletableFuture<>();
        final long correlationId = pendingAsyncResponses.register(future, 30_000);
        final CompletableFuture<Result<ERR, T>> mappedFuture = future.thenApply(r -> r.mapError(errorMapper));
        final LuxisAsync<T, ERR> luxisAsync = new LuxisAsync<>(mappedFuture);
        return new CorrelatedAsync<>(correlationId, luxisAsync);
    }
}
