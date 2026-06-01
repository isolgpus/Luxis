package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.pipeline.LoopConfig;
import io.kiw.luxis.web.pipeline.LoopStep;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates expressing an agent-style feedback loop as a single forward-typed pipeline step.
 *
 * <p>This mirrors the shape of asking a model a question, running a tool, feeding the tool result back,
 * and repeating until the model is "done". Here the model/tool round-trip is faked with a resolved
 * future (so it needs no real HTTP), but the control flow is exactly what a real Claude agent loop
 * would use: each iteration returns {@link LoopStep#again} to run another turn or {@link LoopStep#done}
 * to exit the loop with a final response.
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
                .<LoopAgentResponse>loop(LoopConfig.maxIterations(20), ctx -> {
                    iterationCounter.incrementAndGet();
                    final Conversation convo = ctx.in();
                    // Stands in for: call the model, run any requested tool, append the result.
                    return callModelAndRunTools(convo).map(updated ->
                            updated.accumulator() >= updated.target()
                                    ? LoopStep.<Conversation, LoopAgentResponse>done(
                                            new LoopAgentResponse(updated.accumulator(), updated.turns()))
                                    : LoopStep.<Conversation, LoopAgentResponse>again(updated));
                })
                .complete(ctx -> HttpResult.success(ctx.in()));
    }

    private LuxisAsync<Conversation, HttpErrorResponse> callModelAndRunTools(final Conversation convo) {
        final CompletableFuture<Result<HttpErrorResponse, Conversation>> future = new CompletableFuture<>();
        future.complete(Result.success(convo.afterTurn()));
        return new LuxisAsync<>(future);
    }

    private record Conversation(int accumulator, int turns, int target) {
        Conversation afterTurn() {
            return new Conversation(accumulator + 1, turns + 1, target);
        }
    }
}
