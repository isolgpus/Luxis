package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.ErrorMessageResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LuxisPipelineExecutor<SESSION> {

    enum ThreadContext { APPLICATION_CONTEXT, BLOCKING }

    private final Object appState;
    private final Consumer<Exception> exceptionHandler;
    private final ExecutionDispatcher executionDispatcher;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final LuxisPipelineHandler<SESSION> handler;
    private final TransactionExecutor transactionExecutor;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final MessagingComponents messaging;

    public LuxisPipelineExecutor(final Object appState, final Consumer<Exception> exceptionHandler, final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final LuxisPipelineHandler<SESSION> handler) {
        this(appState, exceptionHandler, executionDispatcher, pendingAsyncResponses, handler, null, MessagingComponents.NONE);
    }

    public LuxisPipelineExecutor(final Object appState, final Consumer<Exception> exceptionHandler, final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final LuxisPipelineHandler<SESSION> handler, final DatabaseClient<?, ?, ?> databaseClient) {
        this(appState, exceptionHandler, executionDispatcher, pendingAsyncResponses, handler, databaseClient, MessagingComponents.NONE);
    }

    public LuxisPipelineExecutor(final Object appState, final Consumer<Exception> exceptionHandler, final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final LuxisPipelineHandler<SESSION> handler, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.appState = appState;
        this.exceptionHandler = exceptionHandler;
        this.executionDispatcher = executionDispatcher;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.handler = handler;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
        this.transactionExecutor = databaseClient == null ? null : new TransactionExecutor(databaseClient, executionDispatcher, this.messaging);
    }

    @SuppressWarnings("unchecked")
    public void execute(final SESSION session, final LuxisPipeline<?> pipeline, final Object message) {
        final MapInstruction firstInstruction = pipeline.getApplicationInstructions().getFirst();
        executeInstruction(session, pipeline, firstInstruction, message, ThreadContext.APPLICATION_CONTEXT);
    }

    private <IN, OUT, APP> void executeInstruction(final SESSION session, final LuxisPipeline<?> pipeline, final MapInstruction<IN, OUT, APP, SESSION, ErrorMessageResponse> instruction, final IN message, final ThreadContext currentThread) {
        final ThreadContext requiredThread = instruction.isBlocking ? ThreadContext.BLOCKING : ThreadContext.APPLICATION_CONTEXT;

        runOnThread(requiredThread, currentThread, () -> {
            handleAndContinue(session, pipeline, instruction, message);
        });
    }

    @SuppressWarnings("unchecked")
    private <IN, OUT, APP> void handleAndContinue(final SESSION session, final LuxisPipeline<?> pipeline, final MapInstruction<IN, OUT, APP, SESSION, ErrorMessageResponse> instruction, final IN message) {
        if (instruction.isTransactional) {
            handleTransaction(session, pipeline, instruction, message);
            return;
        }
        if (instruction.isAsync) {
            final CompletableFuture<Result<ErrorMessageResponse, OUT>> future;
            try {
                future = instruction.handleAsync(message, session, (APP) appState, pendingAsyncResponses, databaseClient, messaging);
            } catch (final Exception e) {
                exceptionHandler.accept(e);
                return;
            }

            executionDispatcher.handleOnApplicationContext(future, exceptionHandler, (r) -> {
                try {
                    r.consume(e -> {
                        handler.handleFailure(session, instruction, e);
                    }, q -> {
                        continueChain(session, pipeline, instruction, q, ThreadContext.APPLICATION_CONTEXT);
                    });
                } catch (final Exception e) {
                    exceptionHandler.accept(e);
                }
            });

        } else {
            final ThreadContext afterThread = instruction.isBlocking ? ThreadContext.BLOCKING : ThreadContext.APPLICATION_CONTEXT;
            final Result<ErrorMessageResponse, OUT> result;
            try {
                result = instruction.handle(message, session, (APP) appState);
            } catch (final Exception e) {
                exceptionHandler.accept(e);
                return;
            }
            result.consume(e -> {
                runOnThread(ThreadContext.APPLICATION_CONTEXT, afterThread, () -> {
                    handler.handleFailure(session, instruction, e);
                });
            }, q -> {
                continueChain(session, pipeline, instruction, q, afterThread);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void continueChain(final SESSION session, final LuxisPipeline<?> pipeline, final MapInstruction<?, ?, ?, ?, ?> instruction, final Object result, final ThreadContext currentThread) {
        if (instruction.next().isPresent()) {
            final MapInstruction next = instruction.next().get();
            executeInstruction(session, pipeline, next, result, currentThread);
        } else if (pipeline.shouldSendResponse()) {
            runOnThread(ThreadContext.APPLICATION_CONTEXT, currentThread, () -> {
                handler.sendFinalResponse(session, result);
            });
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

    private <IN, OUT, APP> void handleTransaction(
            final SESSION session,
            final LuxisPipeline<?> pipeline,
            final MapInstruction<IN, OUT, APP, SESSION, ErrorMessageResponse> instruction,
            final IN message) {
        if (transactionExecutor == null) {
            exceptionHandler.accept(new IllegalStateException(
                    "Encountered transactional instruction but no DatabaseClient is registered."));
            return;
        }
        transactionExecutor.execute(session, appState, instruction, message, exceptionHandler, new TransactionExecutor.Callbacks() {
            @Override
            public void onSuccess(final Object finalValue) {
                continueChain(session, pipeline, instruction, finalValue, ThreadContext.APPLICATION_CONTEXT);
            }

            @Override
            public void onSubChainError(final Object errValue) {
                handler.handleFailure(session, instruction, (ErrorMessageResponse) errValue);
            }
        });
    }
}
