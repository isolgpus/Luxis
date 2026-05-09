package io.kiw.luxis.web.internal;

@FunctionalInterface
public interface BlockingAsyncContextFactory<IN, SESSION, BACTX> {
    BACTX create(IN in, SESSION session, PendingAsyncResponses pendingAsyncResponses);
}
