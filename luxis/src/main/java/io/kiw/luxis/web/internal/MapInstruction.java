package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.pipeline.StreamAsyncFlatMapper;
import io.kiw.luxis.web.pipeline.StreamFlatMapper;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MapInstruction<IN, OUT, APP, SESSION, ERR> {
    public final boolean isBlocking;
    public final boolean isAsync;
    public final boolean lastStep;
    public final boolean isTransactional;

    private final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, OUT> consumer;
    @SuppressWarnings("rawtypes")
    private final StreamFlatMapper blockingConsumer;
    private final StreamAsyncFlatMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, ERR, OUT> asyncConsumer;
    @SuppressWarnings("rawtypes")
    private final StreamAsyncFlatMapper asyncBlockingConsumer;
    private final TransactionSubChain<APP, ERR, SESSION> transactionSubChain;

    private final BiFunction<IN, SESSION, ?> blockingContextFactory;
    private final BlockingAsyncContextFactory<IN, SESSION, ?> blockingAsyncContextFactory;
    private final Function<HttpErrorResponse, ERR> errorMapper;

    private boolean isValidation;
    private Optional<MapInstruction<?, ?, ?, ?, ?>> next = Optional.empty();

    @SuppressWarnings("rawtypes")
    private MapInstruction(
            final boolean isBlocking,
            final boolean isAsync,
            final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, OUT> consumer,
            final StreamFlatMapper blockingConsumer,
            final StreamAsyncFlatMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, ERR, OUT> asyncConsumer,
            final StreamAsyncFlatMapper asyncBlockingConsumer,
            final boolean lastStep,
            final BiFunction<IN, SESSION, ?> blockingContextFactory,
            final BlockingAsyncContextFactory<IN, SESSION, ?> blockingAsyncContextFactory,
            final Function<HttpErrorResponse, ERR> errorMapper,
            final TransactionSubChain<APP, ERR, SESSION> transactionSubChain) {
        this.isBlocking = isBlocking;
        this.isAsync = isAsync;
        this.consumer = consumer;
        this.blockingConsumer = blockingConsumer;
        this.asyncConsumer = asyncConsumer;
        this.asyncBlockingConsumer = asyncBlockingConsumer;
        this.lastStep = lastStep;
        this.blockingContextFactory = blockingContextFactory;
        this.blockingAsyncContextFactory = blockingAsyncContextFactory;
        this.errorMapper = errorMapper;
        this.transactionSubChain = transactionSubChain;
        this.isTransactional = transactionSubChain != null;
    }

    public static <IN, OUT, APP, SESSION, ERR> MapInstruction<IN, OUT, APP, SESSION, ERR> nonBlocking(
            final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, OUT> consumer, final boolean lastStep) {
        return new MapInstruction<>(false, false, consumer, null, null, null, lastStep, null, null, null, null);
    }

    public static <IN, OUT, APP, SESSION, ERR> MapInstruction<IN, OUT, APP, SESSION, ERR> blocking(
            final StreamFlatMapper<?, ERR, OUT> consumer,
            final BiFunction<IN, SESSION, ?> contextFactory,
            final boolean lastStep) {
        return new MapInstruction<>(true, false, null, consumer, null, null, lastStep, contextFactory, null, null, null);
    }

    public static <IN, OUT, APP, SESSION, ERR> MapInstruction<IN, OUT, APP, SESSION, ERR> nonBlockingAsync(
            final StreamAsyncFlatMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, ERR, OUT> asyncConsumer,
            final boolean lastStep,
            final Function<HttpErrorResponse, ERR> errorMapper) {
        return new MapInstruction<>(false, true, null, null, asyncConsumer, null, lastStep, null, null, errorMapper, null);
    }

    public static <IN, OUT, APP, SESSION, ERR> MapInstruction<IN, OUT, APP, SESSION, ERR> blockingAsync(
            final StreamAsyncFlatMapper<?, ERR, OUT> asyncBlockingConsumer,
            final BlockingAsyncContextFactory<IN, SESSION, ?> contextFactory,
            final boolean lastStep) {
        return new MapInstruction<>(true, true, null, null, null, asyncBlockingConsumer, lastStep, null, contextFactory, null, null);
    }

    public static <IN, OUT, APP, SESSION, ERR> MapInstruction<IN, OUT, APP, SESSION, ERR> transactional(
            final TransactionSubChain<APP, ERR, SESSION> subChain,
            final boolean lastStep) {
        return new MapInstruction<>(false, true, null, null, null, null, lastStep, null, null, null, subChain);
    }

    public TransactionSubChain<APP, ERR, SESSION> transactionSubChain() {
        return transactionSubChain;
    }

    public void markAsValidation() {
        this.isValidation = true;
    }

    public boolean isValidation() {
        return isValidation;
    }

    public void setNext(final MapInstruction<?, ?, ?, ?, ?> next) {
        this.next = Optional.of(next);
    }

    public Optional<MapInstruction<?, ?, ?, ?, ?>> next() {
        return next;
    }

    @SuppressWarnings("unchecked")
    public Result<ERR, OUT> handle(final IN state, final SESSION session, final APP applicationState) {
        if (consumer != null) {
            return consumer.handle(new RouteContext<>(state, session, applicationState));
        } else if (blockingConsumer != null) {
            return blockingConsumer.handle(blockingContextFactory.apply(state, session));
        }

        throw new UnsupportedOperationException("Unknown consumer");
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Result<ERR, OUT>> handleAsync(final IN state, final SESSION session, final APP applicationState, final PendingAsyncResponses pendingAsyncResponses, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        if (asyncConsumer != null) {
            final MessagingComponents m = messaging != null ? messaging : MessagingComponents.NONE;
            return asyncConsumer.handle(new AsyncRouteContext<>(state, session, applicationState, pendingAsyncResponses, errorMapper, databaseClient, m.outboxStore(), m.drainer()));
        } else if (asyncBlockingConsumer != null) {
            return asyncBlockingConsumer.handle(blockingAsyncContextFactory.create(state, session, pendingAsyncResponses));
        }

        throw new UnsupportedOperationException("Unknown async consumer");
    }
}
