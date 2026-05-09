package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.result.Result;

import java.util.concurrent.CompletableFuture;

public interface StreamAsyncFlatMapper<CTX, ERR, RES> {
    CompletableFuture<Result<ERR, RES>> handle(CTX ctx);
}
