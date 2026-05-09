package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.http.client.LuxisAsync;

@FunctionalInterface
public interface TransactionAsyncMapper<IN, OUT, APP, ERR, SESSION> {
    LuxisAsync<OUT, ERR> handle(TransactionAsyncRouteContext<IN, APP, SESSION, ERR> ctx);
}
