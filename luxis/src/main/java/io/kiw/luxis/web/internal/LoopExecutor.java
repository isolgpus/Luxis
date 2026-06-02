package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.ErrorCause;
import io.kiw.luxis.web.pipeline.ErrorMessageResponseMapper;
import io.kiw.luxis.web.pipeline.LoopStep;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Runs the body of a {@link io.kiw.luxis.web.pipeline.LuxisStream#loop} as a sub-chain of
 * {@link MapInstruction}s, repeatedly, until the body produces {@link LoopStep.Done} or the iteration
 * cap is reached.
 *
 * <p>Unlike {@link TransactionExecutor}, the loop body may contain blocking steps: this executor hops
 * to a worker thread (via {@link ExecutionDispatcher#handleBlocking}) for blocking instructions and
 * back to the application context for everything else — the same thread discipline the top-level
 * {@link LuxisPipelineExecutor} applies, but confined to the sub-chain and looped.
 */
public final class LoopExecutor {

    private enum ThreadContext { APPLICATION_CONTEXT, BLOCKING }

    private final ExecutionDispatcher executionDispatcher;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final MessagingComponents messaging;

    public LoopExecutor(final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.executionDispatcher = executionDispatcher;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    public interface Callbacks {
        void onSuccess(Object finalValue);

        void onError(Object errValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void execute(
            final Object session,
            final Object appState,
            final MapInstruction instruction,
            final Object input,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        final LoopSubChain subChain = instruction.loopSubChain();
        runIteration(session, appState, subChain, subChain.maxIterations(), input, exceptionHandler, callbacks);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runIteration(
            final Object session,
            final Object appState,
            final LoopSubChain subChain,
            final int iterationsRemaining,
            final Object state,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        if (iterationsRemaining <= 0) {
            final ErrorMessageResponseMapper mapper = subChain.errorMapper();
            final Object err = mapper.map(
                    new ErrorMessageResponse("Loop exceeded max iterations of " + subChain.maxIterations()),
                    ErrorCause.ASYNC_ERROR);
            callbacks.onError(err);
            return;
        }
        runStep(session, appState, subChain, iterationsRemaining, 0, state, ThreadContext.APPLICATION_CONTEXT, exceptionHandler, callbacks);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runStep(
            final Object session,
            final Object appState,
            final LoopSubChain subChain,
            final int iterationsRemaining,
            final int idx,
            final Object current,
            final ThreadContext currentThread,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        final List<MapInstruction> steps = subChain.steps();
        if (idx >= steps.size()) {
            final LoopStep<Object, Object> loopStep = (LoopStep<Object, Object>) current;
            loopStep.consume(
                    next -> executionDispatcher.handleOnApplicationContext(() ->
                            runIteration(session, appState, subChain, iterationsRemaining - 1, next, exceptionHandler, callbacks)),
                    done -> executionDispatcher.handleOnApplicationContext(() -> callbacks.onSuccess(done)));
            return;
        }

        final MapInstruction instruction = steps.get(idx);
        final ThreadContext requiredThread = instruction.isBlocking ? ThreadContext.BLOCKING : ThreadContext.APPLICATION_CONTEXT;
        runOnThread(requiredThread, currentThread, () ->
                handleStep(session, appState, subChain, iterationsRemaining, idx, current, requiredThread, exceptionHandler, callbacks));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void handleStep(
            final Object session,
            final Object appState,
            final LoopSubChain subChain,
            final int iterationsRemaining,
            final int idx,
            final Object current,
            final ThreadContext requiredThread,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        final MapInstruction instruction = subChain.steps().get(idx);

        if (instruction.isAsync) {
            final CompletableFuture<Result> future;
            try {
                future = instruction.handleAsync(current, session, appState, pendingAsyncResponses, databaseClient, messaging);
            } catch (final Exception e) {
                executionDispatcher.handleOnApplicationContext(() -> exceptionHandler.accept(e));
                return;
            }
            executionDispatcher.handleOnApplicationContext(future, exceptionHandler, (Result r) ->
                    r.consume(
                            err -> callbacks.onError(err),
                            ok -> runStep(session, appState, subChain, iterationsRemaining, idx + 1, ok, ThreadContext.APPLICATION_CONTEXT, exceptionHandler, callbacks)));
        } else {
            final Result result;
            try {
                result = instruction.handle(current, session, appState);
            } catch (final Exception e) {
                executionDispatcher.handleOnApplicationContext(() -> exceptionHandler.accept(e));
                return;
            }
            result.consume(
                    err -> executionDispatcher.handleOnApplicationContext(() -> callbacks.onError(err)),
                    ok -> runStep(session, appState, subChain, iterationsRemaining, idx + 1, ok, requiredThread, exceptionHandler, callbacks));
        }
    }

    private void runOnThread(final ThreadContext required, final ThreadContext current, final Runnable action) {
        if (current == required) {
            action.run();
        } else if (required == ThreadContext.BLOCKING) {
            executionDispatcher.handleBlocking(action);
        } else {
            executionDispatcher.handleOnApplicationContext(action);
        }
    }
}
