package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpErrorResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class PendingAsyncResponses {
    private final AtomicLong nextId = new AtomicLong(0);
    private final ConcurrentHashMap<Long, PendingEntry> pending = new ConcurrentHashMap<>();
    private final TimeoutScheduler scheduler;
    private final Consumer<Exception> exceptionHandler;

    public PendingAsyncResponses(final TimeoutScheduler scheduler, final Consumer<Exception> exceptionHandler) {
        this.scheduler = scheduler;
        this.exceptionHandler = exceptionHandler;
    }

    @SuppressWarnings("unchecked")
    public <T> long register(final CompletableFuture<Result<HttpErrorResponse, T>> future, final long timeoutMillis) {
        final long id = nextId.getAndIncrement();
        final TimeoutScheduler.Cancellable cancellable = scheduler.schedule(ScheduleType.TIMEOUT, timeoutMillis, () -> expire(id));
        pending.put(id, new PendingEntry((CompletableFuture<Result<HttpErrorResponse, ?>>) (CompletableFuture<?>) future, cancellable));
        return id;
    }

    @SuppressWarnings("unchecked")
    public <T> void complete(final long correlationId, final Result<HttpErrorResponse, T> result) {
        final PendingEntry entry = pending.remove(correlationId);
        if (entry == null) {
            throw new IllegalArgumentException("No pending async response for correlationId: " + correlationId);
        }
        entry.cancellable.cancel();
        entry.future.complete((Result<HttpErrorResponse, ?>) (Result<?, ?>) result);
    }

    private void expire(final long correlationId) {
        final PendingEntry entry = pending.remove(correlationId);
        if (entry == null) {
            return;
        }
        entry.future.complete(Result.error(new HttpErrorResponse(new ErrorMessageResponse("Something went wrong"), ErrorStatusCode.INTERNAL_SERVER_ERROR)));
        exceptionHandler.accept(new RuntimeException("Correlated async response timed out for correlationId: " + correlationId));
    }

    public TimeoutScheduler.Cancellable scheduleTimeout(final long delayMillis, final Runnable action, final ScheduleType scheduleType) {
        return scheduler.schedule(scheduleType, delayMillis, action);
    }

    public void reportException(final Exception e) {
        exceptionHandler.accept(e);
    }

    private record PendingEntry(CompletableFuture<Result<HttpErrorResponse, ?>> future,
                                TimeoutScheduler.Cancellable cancellable) {
    }
}
