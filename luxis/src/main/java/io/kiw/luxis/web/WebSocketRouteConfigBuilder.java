package io.kiw.luxis.web;

import io.kiw.luxis.web.pipeline.BackpressureStrategy;
import io.kiw.luxis.web.pipeline.CorruptWebSocketInputStrategy;
import io.kiw.luxis.web.pipeline.DisconnectSession;
import io.kiw.luxis.web.pipeline.FailedValidationStrategy;
import io.kiw.luxis.web.pipeline.JustSendValidationError;

public class WebSocketRouteConfigBuilder {

    private CorruptWebSocketInputStrategy corruptInputStrategy = DisconnectSession.INSTANCE;
    private FailedValidationStrategy failedValidationStrategy = JustSendValidationError.INSTANCE;
    private BackpressureStrategy backpressureStrategy = BackpressureStrategy.UNBOUNDED_BUFFER;

    public WebSocketRouteConfigBuilder corruptInputStrategy(final CorruptWebSocketInputStrategy corruptInputStrategy) {
        this.corruptInputStrategy = corruptInputStrategy;
        return this;
    }

    public WebSocketRouteConfigBuilder failedValidationStrategy(final FailedValidationStrategy failedValidationStrategy) {
        this.failedValidationStrategy = failedValidationStrategy;
        return this;
    }

    public WebSocketRouteConfigBuilder backpressureStrategy(final BackpressureStrategy backpressureStrategy) {
        this.backpressureStrategy = backpressureStrategy;
        return this;
    }

    public WebSocketRouteConfig build() {
        return new WebSocketRouteConfig(corruptInputStrategy, failedValidationStrategy, backpressureStrategy);
    }
}
