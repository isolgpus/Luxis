package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.messaging.OutboxEvent;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.pipeline.StreamPeeker;
import io.kiw.luxis.web.pipeline.TransactionAsyncRouteContext;
import io.kiw.luxis.web.pipeline.TransactionRouteContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class TransactionExecutor {

    private final DatabaseClient<?, ?, ?> databaseClient;
    private final ExecutionDispatcher executionDispatcher;
    private final MessagingComponents messaging;

    public TransactionExecutor(final DatabaseClient<?, ?, ?> databaseClient, final ExecutionDispatcher executionDispatcher, final MessagingComponents messaging) {
        this.databaseClient = databaseClient;
        this.executionDispatcher = executionDispatcher;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    public interface Callbacks {
        void onSuccess(Object finalValue);

        void onSubChainError(Object errValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public <SESSION> void execute(
            final SESSION session,
            final Object appState,
            final MapInstruction<?, ?, ?, SESSION, ?> instruction,
            final Object input,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        if (databaseClient == null) {
            exceptionHandler.accept(new IllegalStateException(
                    "Encountered transactional instruction but no DatabaseClient is registered."));
            return;
        }
        final DatabaseClient tm = (DatabaseClient) databaseClient;
        final TransactionSubChain subChain = (TransactionSubChain) instruction.transactionSubChain();
        final List<OutboxEvent> outboxBuffer = new ArrayList<>();

        final CompletableFuture<Object> beginCf;
        try {
            beginCf = tm.begin().toCompletionStage().toCompletableFuture();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            return;
        }

        executionDispatcher.handleOnApplicationContext(beginCf, exceptionHandler, tx ->
                runStep(session, appState, subChain, 0, input, tx, tm, outboxBuffer, exceptionHandler, callbacks));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <SESSION> void runStep(
            final SESSION session,
            final Object appState,
            final TransactionSubChain subChain,
            final int idx,
            final Object current,
            final Object tx,
            final DatabaseClient tm,
            final List<OutboxEvent> outboxBuffer,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        final List<TransactionStep> steps = (List) subChain.steps();
        if (idx >= steps.size()) {
            flushOutboxAndCommit(session, appState, subChain, current, tx, tm, outboxBuffer, exceptionHandler, callbacks);
            return;
        }

        final TransactionStep step = steps.get(idx);

        if (step.kind() == TransactionStep.Kind.SYNC) {
            final TransactionRouteContext ctx = new TransactionRouteContext<>(current, appState, session);

            final Result result;
            TransactionStatus.enter();
            try {
                result = step.syncMapper().handle(ctx);
            } catch (final Exception e) {
                rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(e));
                return;
            } finally {
                TransactionStatus.exit();
            }
            result.consume(
                    err -> rollback(tm, tx, exceptionHandler, () -> callbacks.onSubChainError(err)),
                    ok -> runStep(session, appState, subChain, idx + 1, ok, tx, tm, outboxBuffer, exceptionHandler, callbacks));
        } else {
            final LuxisAsync luxisAsync;
            final TransactionAsyncRouteContext ctx = new TransactionAsyncRouteContext<>(current, appState, session, databaseClient, tx, outboxBuffer);

            TransactionStatus.enter();
            try {
                luxisAsync = step.asyncMapper().handle(ctx);
            } catch (final Exception e) {
                rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(e));
                return;
            } finally {
                TransactionStatus.exit();
            }
            final CompletableFuture<Result> cf = luxisAsync.toCompletableFuture();
            final Consumer<Exception> rollbackThenHandle = ex ->
                    rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(ex));
            executionDispatcher.handleOnApplicationContext(cf, rollbackThenHandle, r ->
                    r.consume(errVal -> rollback(tm, tx, exceptionHandler, () -> callbacks.onSubChainError(errVal)),
                            ok -> runStep(session, appState, subChain, idx + 1, ok, tx, tm, outboxBuffer, exceptionHandler, callbacks)));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rollback(final DatabaseClient tm, final Object tx, final Consumer<Exception> exceptionHandler, final Runnable afterRollback) {
        final CompletableFuture<Void> rollbackCf;
        try {
            rollbackCf = tm.rollback(tx).toCompletionStage().toCompletableFuture();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            afterRollback.run();
            return;
        }
        executionDispatcher.handleOnApplicationContext(rollbackCf, exceptionHandler, v -> afterRollback.run());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <SESSION> void flushOutboxAndCommit(
            final SESSION session,
            final Object appState,
            final TransactionSubChain subChain,
            final Object finalValue,
            final Object tx,
            final DatabaseClient tm,
            final List<OutboxEvent> outboxBuffer,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        if (outboxBuffer.isEmpty()) {
            commit(session, appState, subChain, finalValue, tx, tm, exceptionHandler, callbacks);
            return;
        }
        final OutboxStore<?> store = messaging.outboxStore();
        if (store == null) {
            rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(new IllegalStateException(
                    "Events were published inside a transaction but no OutboxStore is registered at Luxis.app(...).")));
            return;
        }
        final OutboxStore rawStore = (OutboxStore) store;
        final CompletableFuture<Void> appendCf;
        try {
            appendCf = rawStore.append(tx, outboxBuffer).toCompletionStage().toCompletableFuture();
        } catch (final Exception e) {
            rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(e));
            return;
        }
        executionDispatcher.handleOnApplicationContext(appendCf, ex ->
                        rollback(tm, tx, exceptionHandler, () -> exceptionHandler.accept(ex)),
                v -> commit(session, appState, subChain, finalValue, tx, tm, exceptionHandler, callbacks));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <SESSION> void commit(
            final SESSION session,
            final Object appState,
            final TransactionSubChain subChain,
            final Object finalValue,
            final Object tx,
            final DatabaseClient tm,
            final Consumer<Exception> exceptionHandler,
            final Callbacks callbacks) {
        final CompletableFuture<Void> commitCf;
        try {
            commitCf = tm.commit(tx).toCompletionStage().toCompletableFuture();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            return;
        }
        executionDispatcher.handleOnApplicationContext(commitCf, exceptionHandler, v -> {
            kickDrainer(exceptionHandler);
            fireOnCompletionHooks(subChain, finalValue, tx, tm, session, appState, exceptionHandler);
            callbacks.onSuccess(finalValue);
        });
    }

    private void kickDrainer(final Consumer<Exception> exceptionHandler) {
        final OutboxDrainer drainer = messaging.drainer();
        if (drainer == null) {
            return;
        }
        try {
            drainer.kick();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <SESSION> void fireOnCompletionHooks(
            final TransactionSubChain subChain,
            final Object finalValue,
            final Object tx,
            final DatabaseClient tm,
            final SESSION session,
            final Object appState,
            final Consumer<Exception> exceptionHandler) {
        final List<StreamPeeker> hooks = (List) subChain.onCompletionHooks();
        if (hooks.isEmpty()) {
            return;
        }
        final CompletableFuture<Void> onCommittedCf;
        try {
            onCommittedCf = tm.onCommitted(tx, () -> executionDispatcher.handleOnApplicationContext(() -> {
                final TransactionRouteContext ctx = new TransactionRouteContext<>(finalValue, appState, session);
                for (final StreamPeeker hook : hooks) {
                    try {
                        hook.handle(ctx);
                    } catch (final Exception e) {
                        exceptionHandler.accept(e);
                    }
                }
            })).toCompletionStage().toCompletableFuture();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            return;
        }
        onCommittedCf.exceptionally(err -> {
            final Throwable cause = err instanceof CompletionException ? err.getCause() : err;
            exceptionHandler.accept(cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
            return null;
        });
    }
}
