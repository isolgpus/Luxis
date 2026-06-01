package io.kiw.luxis.web.pipeline;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The outcome of a single iteration of a {@link LuxisStream#loop} body.
 *
 * <p>A loop body transforms the loop state {@code S} and returns either:
 * <ul>
 *     <li>{@link #again(Object)} — feed a new state back into the loop and run another iteration, or</li>
 *     <li>{@link #done(Object)} — stop looping and continue the pipeline with a final value {@code OUT}.</li>
 * </ul>
 *
 * <p>This is the trampoline that lets a feedback loop stay forward-typed: the iteration type {@code S}
 * is preserved across the {@code again} branch, while {@code done} projects out the exit type {@code OUT}.
 */
public sealed interface LoopStep<S, OUT> permits LoopStep.Again, LoopStep.Done {

    static <S, OUT> LoopStep<S, OUT> again(final S next) {
        return new Again<>(next);
    }

    static <S, OUT> LoopStep<S, OUT> done(final OUT result) {
        return new Done<>(result);
    }

    void consume(Consumer<S> onAgain, Consumer<OUT> onDone);

    <R> R fold(Function<S, R> onAgain, Function<OUT, R> onDone);

    record Again<S, OUT>(S next) implements LoopStep<S, OUT> {
        @Override
        public void consume(final Consumer<S> onAgain, final Consumer<OUT> onDone) {
            onAgain.accept(next);
        }

        @Override
        public <R> R fold(final Function<S, R> onAgain, final Function<OUT, R> onDone) {
            return onAgain.apply(next);
        }
    }

    record Done<S, OUT>(OUT result) implements LoopStep<S, OUT> {
        @Override
        public void consume(final Consumer<S> onAgain, final Consumer<OUT> onDone) {
            onDone.accept(result);
        }

        @Override
        public <R> R fold(final Function<S, R> onAgain, final Function<OUT, R> onDone) {
            return onDone.apply(result);
        }
    }
}
