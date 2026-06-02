package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.internal.AsyncRouteContext;
import io.kiw.luxis.web.internal.RestrictedBlockingAsyncRouteContext;
import io.kiw.luxis.web.internal.RestrictedBlockingRouteContext;
import io.kiw.luxis.web.internal.RouteContext;

import java.util.function.Function;

/**
 * The body builder for {@link LuxisStream#loop}. Offers the same step vocabulary as the outer
 * pipeline — {@code map} (event loop), {@code blockingMap} (worker thread), {@code asyncMap}
 * (non-blocking async) — so a single loop iteration reads like a normal Luxis pipeline.
 *
 * <p>The body transforms the loop state {@code T}. It is terminated with {@link #until}, which decides
 * per iteration whether to run again with a new state or exit the loop with a final value.
 */
public final class LoopStream<T, APP, ERR, SESSION> {

    private final LuxisStream<T, APP, Void, ERR, SESSION> delegate;

    LoopStream(final LuxisStream<T, APP, Void, ERR, SESSION> delegate) {
        this.delegate = delegate;
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> map(final StreamMapper<RouteContext<T, APP, SESSION>, OUT> mapper) {
        return new LoopStream<>(delegate.map(mapper));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> flatMap(final StreamFlatMapper<RouteContext<T, APP, SESSION>, ERR, OUT> mapper) {
        return new LoopStream<>(delegate.flatMap(mapper));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> blockingMap(final StreamMapper<RestrictedBlockingRouteContext<T>, OUT> mapper) {
        return new LoopStream<>(delegate.blockingMap(mapper));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> blockingFlatMap(final StreamFlatMapper<RestrictedBlockingRouteContext<T>, ERR, OUT> mapper) {
        return new LoopStream<>(delegate.blockingFlatMap(mapper));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> asyncMap(final StreamAsyncMapper<AsyncRouteContext<T, APP, SESSION, ERR>, OUT, ERR> handler) {
        return new LoopStream<>(delegate.asyncMap(handler));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> asyncMap(final StreamAsyncMapper<AsyncRouteContext<T, APP, SESSION, ERR>, OUT, ERR> handler, final AsyncMapConfig config) {
        return new LoopStream<>(delegate.asyncMap(handler, config));
    }

    public <OUT> LoopStream<OUT, APP, ERR, SESSION> asyncBlockingMap(final StreamAsyncMapper<RestrictedBlockingAsyncRouteContext<T, ERR>, OUT, ERR> handler) {
        return new LoopStream<>(delegate.asyncBlockingMap(handler));
    }

    public LoopStream<T, APP, ERR, SESSION> peek(final StreamPeeker<RouteContext<T, APP, SESSION>> peeker) {
        return new LoopStream<>(delegate.peek(peeker));
    }

    public LoopStream<T, APP, ERR, SESSION> blockingPeek(final StreamPeeker<RestrictedBlockingRouteContext<T>> peeker) {
        return new LoopStream<>(delegate.blockingPeek(peeker));
    }

    /**
     * Terminates the loop body. For each completed iteration {@code exit} decides whether to run the
     * body again with a new state ({@link LoopStep#again}) or stop and continue the outer pipeline with
     * a final value ({@link LoopStep#done}).
     */
    public <OUT> CompletedLoop<T, OUT, APP, ERR, SESSION> until(final Function<T, LoopStep<T, OUT>> exit) {
        final LuxisStream<LoopStep<T, OUT>, APP, Void, ERR, SESSION> finalStream =
                delegate.map(ctx -> exit.apply(ctx.in()));
        return new CompletedLoop<>(finalStream.instructionChain);
    }
}
