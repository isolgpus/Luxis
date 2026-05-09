package io.kiw.luxis.web;

import io.kiw.luxis.web.pipeline.BackpressureStrategy;
import io.kiw.luxis.web.pipeline.CorruptWebSocketInputStrategy;
import io.kiw.luxis.web.pipeline.FailedValidationStrategy;

public record WebSocketRouteConfig(CorruptWebSocketInputStrategy corruptInputStrategy,
                                   FailedValidationStrategy failedValidationStrategy,
                                   BackpressureStrategy backpressureStrategy) {
}
