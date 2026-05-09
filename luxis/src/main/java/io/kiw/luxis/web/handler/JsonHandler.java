package io.kiw.luxis.web.handler;

import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;

public interface JsonHandler<IN, OUT, APP> {

    LuxisPipeline<OUT> handle(HttpStream<IN, APP> e);

}
