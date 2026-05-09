package io.kiw.luxis.web.http.client;

import io.kiw.luxis.result.Result;

import java.util.concurrent.CompletableFuture;

public final class LuxisAsync<T, ERR> {
    private final CompletableFuture<Result<ERR, T>> future;

    public LuxisAsync(final CompletableFuture<Result<ERR, T>> future) {
        this.future = future;
    }

    public <R> LuxisAsync<R, ERR> map(final java.util.function.Function<T, R> mapper) {
        return new LuxisAsync<>(future.thenApply(result -> result.map(mapper)));
    }

    public CompletableFuture<Result<ERR, T>> toCompletableFuture() {
        return future;
    }

}
