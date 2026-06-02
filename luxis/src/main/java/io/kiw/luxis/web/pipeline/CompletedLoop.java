package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.internal.MapInstruction;

import java.util.List;

/**
 * A {@link LoopStream} that has been terminated with {@link LoopStream#until}. Produced by the loop
 * body builder passed to {@link LuxisStream#loop}; carries the captured body instructions.
 */
public final class CompletedLoop<S, OUT, APP, ERR, SESSION> {

    private final List<MapInstruction> subChain;

    CompletedLoop(final List<MapInstruction> subChain) {
        this.subChain = subChain;
    }

    List<MapInstruction> subChain() {
        return subChain;
    }
}
