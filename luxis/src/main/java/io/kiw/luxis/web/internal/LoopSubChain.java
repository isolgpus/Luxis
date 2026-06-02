package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.pipeline.ErrorMessageResponseMapper;

import java.util.List;

/**
 * The captured body of a {@link io.kiw.luxis.web.pipeline.LuxisStream#loop} as an ordered list of
 * {@link MapInstruction}s. The body transforms the loop state and ends with an instruction that
 * produces a {@link io.kiw.luxis.web.pipeline.LoopStep}, telling the {@link LoopExecutor} whether to
 * run another iteration or exit.
 */
public final class LoopSubChain {

    private final List<MapInstruction> steps;
    private final int maxIterations;
    private final ErrorMessageResponseMapper<?> errorMapper;

    public LoopSubChain(final List<MapInstruction> steps, final int maxIterations, final ErrorMessageResponseMapper<?> errorMapper) {
        this.steps = steps;
        this.maxIterations = maxIterations;
        this.errorMapper = errorMapper;
    }

    public List<MapInstruction> steps() {
        return steps;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public ErrorMessageResponseMapper<?> errorMapper() {
        return errorMapper;
    }
}
