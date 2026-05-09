package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.pipeline.StreamFlatMapper;
import io.kiw.luxis.web.pipeline.TransactionAsyncMapper;
import io.kiw.luxis.web.pipeline.TransactionRouteContext;

public final class TransactionStep<IN, OUT, APP, ERR, SESSION> {
    public enum Kind { SYNC, ASYNC }

    private final Kind kind;
    private final StreamFlatMapper<TransactionRouteContext<IN, APP, SESSION>, ERR, OUT> syncMapper;
    private final TransactionAsyncMapper<IN, OUT, APP, ERR, SESSION> asyncMapper;

    private TransactionStep(
            final Kind kind,
            final StreamFlatMapper<TransactionRouteContext<IN, APP, SESSION>, ERR, OUT> syncMapper,
            final TransactionAsyncMapper<IN, OUT, APP, ERR, SESSION> asyncMapper) {
        this.kind = kind;
        this.syncMapper = syncMapper;
        this.asyncMapper = asyncMapper;
    }

    public static <IN, OUT, APP, ERR, SESSION> TransactionStep<IN, OUT, APP, ERR, SESSION> sync(
            final StreamFlatMapper<TransactionRouteContext<IN, APP, SESSION>, ERR, OUT> mapper) {
        return new TransactionStep<>(Kind.SYNC, mapper, null);
    }

    public static <IN, OUT, APP, ERR, SESSION> TransactionStep<IN, OUT, APP, ERR, SESSION> async(
            final TransactionAsyncMapper<IN, OUT, APP, ERR, SESSION> mapper) {
        return new TransactionStep<>(Kind.ASYNC, null, mapper);
    }

    public Kind kind() {
        return kind;
    }

    public StreamFlatMapper<TransactionRouteContext<IN, APP, SESSION>, ERR, OUT> syncMapper() {
        return syncMapper;
    }

    public TransactionAsyncMapper<IN, OUT, APP, ERR, SESSION> asyncMapper() {
        return asyncMapper;
    }
}
