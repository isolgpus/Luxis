package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.internal.TransactionStep;
import io.kiw.luxis.web.internal.TransactionSubChain;

import java.util.ArrayList;
import java.util.List;

public final class TransactionStream<T, APP, ERR, SESSION> {
    private final List<TransactionStep<?, ?, APP, ERR, SESSION>> steps;
    private final List<StreamPeeker<TransactionRouteContext<?, APP, SESSION>>> onCompletionHooks;

    public TransactionStream() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    private TransactionStream(
            final List<TransactionStep<?, ?, APP, ERR, SESSION>> steps,
            final List<StreamPeeker<TransactionRouteContext<?, APP, SESSION>>> onCompletionHooks) {
        this.steps = steps;
        this.onCompletionHooks = onCompletionHooks;
    }

    public <OUT> TransactionStream<OUT, APP, ERR, SESSION> map(final StreamMapper<TransactionRouteContext<T, APP, SESSION>, OUT> mapper) {
        return flatMap(ctx -> Result.success(mapper.handle(ctx)));
    }

    public <OUT> TransactionStream<OUT, APP, ERR, SESSION> flatMap(final StreamFlatMapper<TransactionRouteContext<T, APP, SESSION>, ERR, OUT> mapper) {
        steps.add(TransactionStep.sync(mapper));
        return new TransactionStream<>(steps, onCompletionHooks);
    }

    public <OUT> TransactionStream<OUT, APP, ERR, SESSION> asyncMap(final TransactionAsyncMapper<T, OUT, APP, ERR, SESSION> mapper) {
        steps.add(TransactionStep.async(mapper));
        return new TransactionStream<>(steps, onCompletionHooks);
    }

    public <X> TransactionStream<T, APP, ERR, SESSION> asyncPeek(final TransactionAsyncMapper<T, X, APP, ERR, SESSION> mapper) {
        final TransactionAsyncMapper<T, T, APP, ERR, SESSION> wrapped = ctx -> {
            final T input = ctx.in();
            return mapper.handle(ctx).map(ignore -> input);
        };
        steps.add(TransactionStep.async(wrapped));
        return new TransactionStream<>(steps, onCompletionHooks);
    }

    public TransactionStream<T, APP, ERR, SESSION> peek(final StreamPeeker<TransactionRouteContext<T, APP, SESSION>> peeker) {
        return map(ctx -> {
            peeker.handle(ctx);
            return ctx.in();
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public TransactionStream<T, APP, ERR, SESSION> onCompletion(final StreamPeeker<TransactionRouteContext<T, APP, SESSION>> hook) {
        onCompletionHooks.add((StreamPeeker) hook);
        return this;
    }

    public CompletedTransaction<T, APP, ERR, SESSION> commit() {
        return new CompletedTransaction<>(new TransactionSubChain<>(steps, onCompletionHooks));
    }
}
