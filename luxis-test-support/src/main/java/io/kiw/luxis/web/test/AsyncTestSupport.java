package io.kiw.luxis.web.test;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.client.LuxisAsync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AsyncTestSupport {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "luxis-async-test-delay");
        thread.setDaemon(true);
        return thread;
    });

    private static final long DELAY_MS = 50L;

    private AsyncTestSupport() {
    }

    public static <T, ERR> LuxisAsync<T, ERR> completed(final T value) {
        final CompletableFuture<Result<ERR, T>> future = new CompletableFuture<>();
        SCHEDULER.schedule(() -> future.complete(Result.success(value)), DELAY_MS, TimeUnit.MILLISECONDS);
        return new LuxisAsync<>(future);
    }

    public static <T, ERR> LuxisAsync<T, ERR> failed(final Throwable throwable) {
        final CompletableFuture<Result<ERR, T>> future = new CompletableFuture<>();
        SCHEDULER.schedule(() -> future.completeExceptionally(throwable), DELAY_MS, TimeUnit.MILLISECONDS);
        return new LuxisAsync<>(future);
    }

    public static <T, ERR> LuxisAsync<T, ERR> failed(final ERR failure) {
        final CompletableFuture<Result<ERR, T>> future = new CompletableFuture<>();
        SCHEDULER.schedule(() -> future.complete(Result.<ERR, T>error(failure)), DELAY_MS, TimeUnit.MILLISECONDS);
        return new LuxisAsync<>(future);
    }
}
