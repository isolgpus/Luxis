package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.internal.LuxisPipeline;

public interface WebSocketHandler<IN, OUT, APP, RESP, ERR, SESSION> {
    LuxisPipeline<OUT> handle(LuxisStream<IN, APP, RESP, ERR, SESSION> stream);
}
