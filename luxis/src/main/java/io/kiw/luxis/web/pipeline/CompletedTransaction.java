package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.internal.TransactionSubChain;

public final class CompletedTransaction<OUT, APP, ERR, SESSION> {
    private final TransactionSubChain<APP, ERR, SESSION> subChain;

    public CompletedTransaction(final TransactionSubChain<APP, ERR, SESSION> subChain) {
        this.subChain = subChain;
    }

    public TransactionSubChain<APP, ERR, SESSION> subChain() {
        return subChain;
    }
}
