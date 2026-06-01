package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.AsyncRouteContext;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.internal.MapInstruction;
import io.kiw.luxis.web.internal.MessagingComponents;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.RestrictedBlockingAsyncRouteContext;
import io.kiw.luxis.web.internal.RestrictedBlockingRouteContext;
import io.kiw.luxis.web.internal.RouteContext;
import io.kiw.luxis.web.internal.ScheduleType;
import io.kiw.luxis.web.internal.TimeoutScheduler;
import io.kiw.luxis.web.internal.ender.Ender;
import io.kiw.luxis.web.validation.Validator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class LuxisStream<IN, APP, RESP, ERR, SESSION> {
    protected final List<MapInstruction> instructionChain;
    protected final APP applicationState;
    protected final PendingAsyncResponses pendingAsyncResponses;
    protected final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper;
    protected final Ender ender;
    protected final DatabaseClient<?, ?, ?> databaseClient;
    protected final MessagingComponents messaging;


    public LuxisStream(final List<MapInstruction> instructionChain, final APP applicationState, final PendingAsyncResponses pendingAsyncResponses, final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper, final Ender ender, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.instructionChain = instructionChain;
        this.applicationState = applicationState;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.errorMessageResponseMapper = errorMessageResponseMapper;
        this.ender = ender;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void appendInstruction(final MapInstruction<?, ?, ?, ?, ?> e) {
        if (!instructionChain.isEmpty()) {
            instructionChain.getLast().setNext(e);
        }
        instructionChain.add(e);
    }

    protected void appendValidation(final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, IN> mapper) {
        final MapInstruction<IN, IN, APP, SESSION, ERR> e = MapInstruction.nonBlocking(mapper, false);
        e.markAsValidation();
        appendInstruction(e);
    }

    public LuxisStream<IN, APP, RESP, ERR, SESSION> validate(final Consumer<Validator<IN, ERR>> config) {
        appendValidation(ctx -> {
            final Validator<IN, ERR> v = new Validator<>(ctx.in(), "", errorMessageResponseMapper);
            config.accept(v);
            return v.toResult();
        });
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> map(final StreamMapper<RouteContext<IN, APP, SESSION>, OUT> flowHandler) {
        return flatMap(ctx -> Result.success(flowHandler.handle(ctx)));
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> flatMap(final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, OUT> mapper) {
        final MapInstruction<IN, OUT, APP, SESSION, ERR> e = MapInstruction.nonBlocking(mapper, false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    public LuxisStream<IN, APP, RESP, ERR, SESSION> peek(final StreamPeeker<RouteContext<IN, APP, SESSION>> peeker) {
        return map(ctx -> {
            peeker.handle(ctx);
            return ctx.in();
        });
    }

    public LuxisStream<IN, APP, RESP, ERR, SESSION> blockingPeek(final StreamPeeker<RestrictedBlockingRouteContext<IN>> peeker) {
        return blockingMap(ctx -> {
            peeker.handle(ctx);
            return ctx.in();
        });
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> blockingMap(final StreamMapper<RestrictedBlockingRouteContext<IN>, OUT> flowHandler) {
        return blockingFlatMap(ctx -> Result.success(flowHandler.handle(ctx)));
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> blockingFlatMap(final StreamFlatMapper<RestrictedBlockingRouteContext<IN>, ERR, OUT> mapper) {
        final MapInstruction<IN, OUT, Object, SESSION, ERR> e =
                MapInstruction.blocking(mapper, (in, session) -> new RestrictedBlockingRouteContext<>(in), false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> asyncMap(final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, OUT, ERR> handler) {
        return asyncMap(handler, AsyncMapConfig.defaultConfig());
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> asyncMap(final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, OUT, ERR> handler, final AsyncMapConfig config) {
        final StreamAsyncFlatMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, ERR, OUT> wrapper = ctx -> {
            final CompletableFuture<Result<ERR, OUT>> resultFuture = new CompletableFuture<>();
            if (config.maxRetries > 0) {
                AsyncRetryExecutor.executeWithRetry(
                        resultFuture,
                        () -> {
                            final LuxisAsync<OUT, ERR> luxisAsync = handler.handle(ctx);
                            return luxisAsync.toCompletableFuture();
                        },
                        config,
                        pendingAsyncResponses,
                        config.maxRetries
                );
            } else {
                pendingAsyncResponses.scheduleTimeout(config.timeoutMillis, () -> {
                    resultFuture.completeExceptionally(new RuntimeException("Async response timed out"));
                }, ScheduleType.TIMEOUT);
                final LuxisAsync<OUT, ERR> luxisAsync = handler.handle(ctx);
                luxisAsync.toCompletableFuture().whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        resultFuture.completeExceptionally(throwable);
                    } else {
                        resultFuture.complete(result);
                    }
                });
            }
            return resultFuture.exceptionally(throwable -> {
                final Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                pendingAsyncResponses.reportException(
                        cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
                return Result.error(errorMessageResponseMapper.map(new ErrorMessageResponse("Something went wrong"), ErrorCause.ASYNC_ERROR));
            });
        };
        final MapInstruction<IN, OUT, APP, SESSION, ERR> e = MapInstruction.nonBlockingAsync(wrapper, false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    public <X> LuxisStream<IN, APP, RESP, ERR, SESSION> asyncPeek(final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, X, ERR> handler) {
        return asyncMap(ctx -> {
            final IN input = ctx.in();
            return handler.handle(ctx).map(ignore -> input);
        });
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> asyncBlockingMap(final StreamAsyncMapper<RestrictedBlockingAsyncRouteContext<IN, ERR>, OUT, ERR> handler) {
        return asyncBlockingMap(handler, AsyncMapConfig.defaultConfig());
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> asyncBlockingMap(final StreamAsyncMapper<RestrictedBlockingAsyncRouteContext<IN, ERR>, OUT, ERR> handler, final AsyncMapConfig config) {
        final StreamAsyncFlatMapper<RestrictedBlockingAsyncRouteContext<IN, ERR>, ERR, OUT> wrapper = ctx -> {
            final CompletableFuture<Result<ERR, OUT>> resultFuture = new CompletableFuture<>();
            if (config.maxRetries > 0) {
                AsyncRetryExecutor.executeWithRetry(
                        resultFuture,
                        () -> {
                            final LuxisAsync<OUT, ERR> luxisAsync = handler.handle(ctx);
                            return luxisAsync.toCompletableFuture()
                                    .thenApply(result -> result);
                        },
                        config,
                        pendingAsyncResponses,
                        config.maxRetries
                );
            } else {
                pendingAsyncResponses.scheduleTimeout(config.timeoutMillis, () -> {
                    resultFuture.completeExceptionally(new RuntimeException("Async response timed out"));
                }, ScheduleType.TIMEOUT);
                final LuxisAsync<OUT, ERR> luxisAsync = handler.handle(ctx);
                luxisAsync.toCompletableFuture().whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        resultFuture.completeExceptionally(throwable);
                    } else {
                        resultFuture.complete(result);
                    }
                });
            }
            return resultFuture.exceptionally(throwable -> {
                final Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                pendingAsyncResponses.reportException(
                        cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
                return Result.error(errorMessageResponseMapper.map(new ErrorMessageResponse("Something went wrong"), ErrorCause.ASYNC_ERROR));
            });
        };
        final MapInstruction<IN, OUT, Object, SESSION, ERR> e =
                MapInstruction.blockingAsync(wrapper, (in, session) -> new RestrictedBlockingAsyncRouteContext<>(in), false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> inTransaction(
            final Function<TransactionStream<IN, APP, ERR, SESSION>, CompletedTransaction<OUT, APP, ERR, SESSION>> builder) {
        if (databaseClient == null) {
            throw new IllegalStateException("Cannot call .inTransaction(...) — no DatabaseClient registered at Luxis.app(...).");
        }
        final CompletedTransaction<OUT, APP, ERR, SESSION> completed = builder.apply(new TransactionStream<>());
        final MapInstruction<IN, OUT, APP, SESSION, ERR> e = MapInstruction.transactional(completed.subChain(), false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    /**
     * Runs a bounded feedback loop as a single forward-typed pipeline step.
     *
     * <p>The loop iterates over the current stream value of type {@code IN}. Each iteration runs the
     * async {@code body}, which returns a {@link LoopStep}: {@link LoopStep#again} feeds a new {@code IN}
     * back into the loop, while {@link LoopStep#done} exits and continues the pipeline with an {@code OUT}.
     *
     * <p>The loop is one async instruction in the chain — everything before and after it stays a flat,
     * forward-typed pipeline. Recursion is encoded as data ({@link LoopStep}) exactly as errors are
     * encoded as data ({@link Result}). The loop is bounded by {@link LoopConfig}; exceeding the iteration
     * cap ends the loop with an error rather than spinning forever.
     */
    public <OUT> LuxisStream<OUT, APP, RESP, ERR, SESSION> loop(
            final LoopConfig config,
            final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, LoopStep<IN, OUT>, ERR> body) {
        final StreamAsyncFlatMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, ERR, OUT> wrapper = ctx -> {
            final CompletableFuture<Result<ERR, OUT>> resultFuture = new CompletableFuture<>();
            final SESSION session = ctx.session();
            final Function<IN, AsyncRouteContext<IN, APP, SESSION, ERR>> contextFactory =
                    state -> new AsyncRouteContext<>(state, session, applicationState, databaseClient, messaging.outboxStore(), messaging.drainer());
            runLoopIteration(resultFuture, ctx.in(), config.maxIterations, config, contextFactory, body);
            return resultFuture.exceptionally(throwable -> {
                final Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                pendingAsyncResponses.reportException(
                        cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
                return Result.error(errorMessageResponseMapper.map(new ErrorMessageResponse("Something went wrong"), ErrorCause.ASYNC_ERROR));
            });
        };
        final MapInstruction<IN, OUT, APP, SESSION, ERR> e = MapInstruction.nonBlockingAsync(wrapper, false);
        appendInstruction(e);
        return new LuxisStream<>(instructionChain, applicationState, pendingAsyncResponses, errorMessageResponseMapper, ender, databaseClient, messaging);
    }

    /**
     * Convenience form of {@link #loop} for when the loop state is also the loop output. Runs {@code body}
     * repeatedly while {@code continueWhile} holds on the value it produces, then continues the pipeline
     * with the final value.
     */
    public LuxisStream<IN, APP, RESP, ERR, SESSION> loopWhile(
            final Predicate<IN> continueWhile,
            final LoopConfig config,
            final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, IN, ERR> body) {
        return this.<IN>loop(config, ctx -> body.handle(ctx).map(next ->
                continueWhile.test(next)
                        ? LoopStep.<IN, IN>again(next)
                        : LoopStep.<IN, IN>done(next)));
    }

    private <OUT> void runLoopIteration(
            final CompletableFuture<Result<ERR, OUT>> resultFuture,
            final IN state,
            final int iterationsRemaining,
            final LoopConfig config,
            final Function<IN, AsyncRouteContext<IN, APP, SESSION, ERR>> contextFactory,
            final StreamAsyncMapper<AsyncRouteContext<IN, APP, SESSION, ERR>, LoopStep<IN, OUT>, ERR> body) {
        if (iterationsRemaining <= 0) {
            resultFuture.complete(Result.error(errorMessageResponseMapper.map(
                    new ErrorMessageResponse("Loop exceeded max iterations of " + config.maxIterations), ErrorCause.ASYNC_ERROR)));
            return;
        }

        final AtomicBoolean handled = new AtomicBoolean(false);
        final TimeoutScheduler.Cancellable timeout = pendingAsyncResponses.scheduleTimeout(
                config.iterationTimeoutMillis,
                () -> {
                    if (handled.compareAndSet(false, true)) {
                        resultFuture.completeExceptionally(new RuntimeException("Loop iteration timed out"));
                    }
                }, ScheduleType.TIMEOUT);

        final CompletableFuture<Result<ERR, LoopStep<IN, OUT>>> attempt;
        try {
            attempt = body.handle(contextFactory.apply(state)).toCompletableFuture();
        } catch (final Exception e) {
            timeout.cancel();
            if (handled.compareAndSet(false, true)) {
                resultFuture.completeExceptionally(e);
            }
            return;
        }

        attempt.whenComplete((result, throwable) -> {
            if (!handled.compareAndSet(false, true)) {
                return;
            }
            timeout.cancel();
            if (throwable != null) {
                resultFuture.completeExceptionally(throwable);
                return;
            }
            result.consume(
                    error -> resultFuture.complete(Result.error(error)),
                    step -> step.consume(
                            next -> runLoopIteration(resultFuture, next, iterationsRemaining - 1, config, contextFactory, body),
                            done -> resultFuture.complete(Result.success(done))
                    )
            );
        });
    }

    public <OUT> LuxisPipeline<OUT> complete(final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, OUT> mapper) {
        final MapInstruction<IN, OUT, APP, SESSION, ERR> e = MapInstruction.nonBlocking(mapper, ender != null);
        appendInstruction(e);
        return new LuxisPipeline<>(instructionChain, applicationState, true, ender);
    }

    public LuxisPipeline<IN> complete() {
        final StreamFlatMapper<RouteContext<IN, APP, SESSION>, ERR, IN> mapper =
                ctx -> Result.success(ctx.in());
        final MapInstruction<IN, IN, APP, SESSION, ERR> e = MapInstruction.nonBlocking(mapper, ender != null);
        appendInstruction(e);
        return new LuxisPipeline<>(instructionChain, applicationState, true, ender);
    }

    public LuxisPipeline<Void> completeWithNoResponse() {
        return new LuxisPipeline<>(instructionChain, applicationState, false, ender);
    }

    public <OUT> LuxisPipeline<OUT> blockingComplete(final StreamFlatMapper<RestrictedBlockingRouteContext<IN>, ERR, OUT> mapper) {
        final MapInstruction<IN, OUT, Object, SESSION, ERR> e =
                MapInstruction.blocking(mapper, (in, session) -> new RestrictedBlockingRouteContext<>(in), ender != null);
        appendInstruction(e);
        return new LuxisPipeline<>(instructionChain, applicationState, true, ender);
    }
}
