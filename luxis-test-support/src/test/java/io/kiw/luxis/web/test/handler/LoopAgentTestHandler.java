package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.pipeline.LoopConfig;
import io.kiw.luxis.web.pipeline.LoopStep;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates a full-fidelity agent feedback loop: each iteration runs a <em>blocking</em> step on a
 * worker thread (standing in for the blocking Anthropic SDK call plus tool execution) followed by a
 * non-blocking step on the application context, then decides whether to loop again or exit.
 *
 * <p>The loop body uses the same vocabulary as the outer pipeline ({@code blockingMap}, {@code map})
 * and the framework hops threads between steps exactly as it would at the top level.
 */
public class LoopAgentTestHandler implements JsonHandler<LoopAgentRequest, LoopAgentResponse, MyApplicationState> {

    private final AtomicLong iterationCounter;

    public LoopAgentTestHandler(final AtomicLong iterationCounter) {
        this.iterationCounter = iterationCounter;
    }

    @Override
    public LuxisPipeline<LoopAgentResponse> handle(final HttpStream<LoopAgentRequest, MyApplicationState> httpStream) {
        return httpStream
                .map(ctx -> new Conversation(0, 0, ctx.in().target))   // seed the loop state
                .<LoopAgentResponse>loop(LoopConfig.maxIterations(20), loop -> loop
                        .blockingMap(ctx -> {                          // blocking model + tool call (worker thread)
                            iterationCounter.incrementAndGet();
                            return ctx.in().afterModelCall();
                        })
                        .map(ctx -> ctx.in().afterAppend())            // append the result (event loop)
                        .until(convo -> convo.accumulator() >= convo.target()
                                ? LoopStep.<Conversation, LoopAgentResponse>done(
                                        new LoopAgentResponse(convo.accumulator(), convo.turns()))
                                : LoopStep.<Conversation, LoopAgentResponse>again(convo)))
                .complete(ctx -> HttpResult.success(ctx.in()));
    }

    private record Conversation(int accumulator, int turns, int target) {
        Conversation afterModelCall() {
            return new Conversation(accumulator + 1, turns, target);
        }

        Conversation afterAppend() {
            return new Conversation(accumulator, turns + 1, target);
        }
    }
}
