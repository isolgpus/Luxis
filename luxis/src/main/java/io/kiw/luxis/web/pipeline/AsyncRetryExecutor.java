package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.ScheduleType;
import io.kiw.luxis.web.internal.TimeoutScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class AsyncRetryExecutor {

    private AsyncRetryExecutor() {
    }

    static <E, T> void executeWithRetry(
            final CompletableFuture<Result<E, T>> resultFuture,
            final Supplier<CompletableFuture<Result<E, T>>> attemptSupplier,
            final AsyncMapConfig config,
            final PendingAsyncResponses pendingAsyncResponses,
            final int retriesRemaining) {

        final AtomicBoolean attemptHandled = new AtomicBoolean(false);

        final TimeoutScheduler.Cancellable timeoutCancellable = pendingAsyncResponses.scheduleTimeout(
                config.timeoutMillis,
                () -> {
                    if (attemptHandled.compareAndSet(false, true)) {
                        handleFailure(resultFuture, attemptSupplier, config, pendingAsyncResponses,
                                retriesRemaining, new RuntimeException("Async response timeout"));
                    }
                }, ScheduleType.TIMEOUT);

        final CompletableFuture<Result<E, T>> attemptFuture;
        try {
            attemptFuture = attemptSupplier.get();
        } catch (final Exception e) {
            timeoutCancellable.cancel();
            if (attemptHandled.compareAndSet(false, true)) {
                handleFailure(resultFuture, attemptSupplier, config, pendingAsyncResponses, retriesRemaining, e);
            }
            return;
        }

        attemptFuture.whenComplete((result, throwable) -> {
            if (attemptHandled.compareAndSet(false, true)) {
                timeoutCancellable.cancel();
                if (throwable != null) {
                    handleFailure(resultFuture, attemptSupplier, config, pendingAsyncResponses,
                            retriesRemaining, throwable);
                } else {
                    result.consume(
                            error -> {
                                if (retriesRemaining > 0) {
                                    scheduleRetry(resultFuture, attemptSupplier, config,
                                            pendingAsyncResponses, retriesRemaining);
                                } else {
                                    resultFuture.complete(result);
                                }
                            },
                            success -> resultFuture.complete(result)
                    );
                }
            }
        });
    }

    private static <E, T> void handleFailure(
            final CompletableFuture<Result<E, T>> resultFuture,
            final Supplier<CompletableFuture<Result<E, T>>> attemptSupplier,
            final AsyncMapConfig config,
            final PendingAsyncResponses pendingAsyncResponses,
            final int retriesRemaining,
            final Throwable cause) {
        if (retriesRemaining > 0) {
            scheduleRetry(resultFuture, attemptSupplier, config, pendingAsyncResponses, retriesRemaining);
        } else {
            resultFuture.completeExceptionally(cause);
        }
    }

    private static <E, T> void scheduleRetry(
            final CompletableFuture<Result<E, T>> resultFuture,
            final Supplier<CompletableFuture<Result<E, T>>> attemptSupplier,
            final AsyncMapConfig config,
            final PendingAsyncResponses pendingAsyncResponses,
            final int retriesRemaining) {
        pendingAsyncResponses.scheduleTimeout(config.retryIntervalMillis, () ->
                executeWithRetry(resultFuture, attemptSupplier, config, pendingAsyncResponses,
                        retriesRemaining - 1), ScheduleType.RETRY);
    }
}
