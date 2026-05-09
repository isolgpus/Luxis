package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.result.Result;

public interface StreamFlatMapper<CTX, ERR, RES> {
    Result<ERR, RES> handle(CTX ctx);
}
