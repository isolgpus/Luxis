package io.kiw.luxis.web.handler;

import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;

public interface JsonFilter<APP> {

    LuxisPipeline<Void> handle(HttpStream<Void, APP> e);

}
