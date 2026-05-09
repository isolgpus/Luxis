package io.kiw.luxis.web.handler;

import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;

import java.util.Map;

public abstract class FileUploadRoute<OUT, APP> {

    public abstract LuxisPipeline<OUT> handle(HttpStream<Map<String, HttpBuffer>, APP> e);

}
