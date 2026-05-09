package io.kiw.luxis.web.pipeline;

public interface StreamPeeker<CTX> {
    void handle(CTX ctx);
}
