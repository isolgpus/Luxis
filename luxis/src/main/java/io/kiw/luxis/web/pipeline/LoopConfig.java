package io.kiw.luxis.web.pipeline;

/**
 * Bounds a {@link LuxisStream#loop} so a feedback loop can never spin forever.
 *
 * <p>A loop is capped by a maximum number of iterations. Individual async steps inside the loop body
 * remain bounded by their own {@link AsyncMapConfig} timeouts, so the loop only needs to bound how
 * many times the body may run.
 */
public final class LoopConfig {

    final int maxIterations;

    private LoopConfig(final int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public static LoopConfig maxIterations(final int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than 0");
        }
        return new LoopConfig(maxIterations);
    }
}
