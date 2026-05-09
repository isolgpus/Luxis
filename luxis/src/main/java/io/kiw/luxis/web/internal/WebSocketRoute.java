package io.kiw.luxis.web.internal;

public class WebSocketRoute<IN> {
    private final Class<IN> messageType;
    private final LuxisPipeline<?> pipeline;

    public WebSocketRoute(final Class<IN> messageType, final LuxisPipeline<?> pipeline) {
        this.messageType = messageType;
        this.pipeline = pipeline;
    }

    public Class<IN> messageType() {
        return messageType;
    }

    public LuxisPipeline<?> pipeline() {
        return pipeline;
    }
}
