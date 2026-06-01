package io.kiw.luxis.web.pipeline;

/**
 * Bounds a {@link LuxisStream#loop} so a feedback loop can never spin forever.
 *
 * <p>Mirrors the safety stance of {@link AsyncMapConfig}: every async step is bounded. A loop is
 * capped by a maximum number of iterations and a per-iteration timeout. Exceeding either ends the
 * loop with an error rather than hanging the request.
 */
public final class LoopConfig {

    final int maxIterations;
    final long iterationTimeoutMillis;

    private LoopConfig(final int maxIterations, final long iterationTimeoutMillis) {
        this.maxIterations = maxIterations;
        this.iterationTimeoutMillis = iterationTimeoutMillis;
    }

    public static LoopConfig maxIterations(final int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than 0");
        }
        return new LoopConfig(maxIterations, 30_000);
    }

    public LoopConfig withIterationTimeoutMillis(final long iterationTimeoutMillis) {
        return new LoopConfig(maxIterations, iterationTimeoutMillis);
    }
}
