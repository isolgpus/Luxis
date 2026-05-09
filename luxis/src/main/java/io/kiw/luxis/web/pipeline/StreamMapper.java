package io.kiw.luxis.web.pipeline;

public interface StreamMapper<CTX, RES> {
    RES handle(CTX ctx);
}
