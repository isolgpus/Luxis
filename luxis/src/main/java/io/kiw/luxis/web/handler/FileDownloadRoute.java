package io.kiw.luxis.web.handler;

import io.kiw.luxis.web.http.DownloadFileResponse;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;

public interface FileDownloadRoute<IN, APP> {
    LuxisPipeline<DownloadFileResponse> handle(HttpStream<IN, APP> e);
}
