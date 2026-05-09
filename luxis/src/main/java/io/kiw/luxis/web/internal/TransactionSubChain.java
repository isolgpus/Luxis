package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.pipeline.StreamPeeker;
import io.kiw.luxis.web.pipeline.TransactionRouteContext;

import java.util.List;

public final class TransactionSubChain<APP, ERR, SESSION> {
    private final List<TransactionStep<?, ?, APP, ERR, SESSION>> steps;
    private final List<StreamPeeker<TransactionRouteContext<?, APP, SESSION>>> onCompletionHooks;

    public TransactionSubChain(
            final List<TransactionStep<?, ?, APP, ERR, SESSION>> steps,
            final List<StreamPeeker<TransactionRouteContext<?, APP, SESSION>>> onCompletionHooks) {
        this.steps = steps;
        this.onCompletionHooks = onCompletionHooks;
    }

    public List<TransactionStep<?, ?, APP, ERR, SESSION>> steps() {
        return steps;
    }

    public List<StreamPeeker<TransactionRouteContext<?, APP, SESSION>>> onCompletionHooks() {
        return onCompletionHooks;
    }
}
