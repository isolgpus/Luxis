package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.FileDownloadRoute;
import io.kiw.luxis.web.http.DownloadFileResponse;
import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

public class FileDownloaderHandler implements FileDownloadRoute<String, MyApplicationState> {
    @Override
    public LuxisPipeline<DownloadFileResponse> handle(final HttpStream<String, MyApplicationState> e) {
        return e.complete(ctx ->
                Result.success(new DownloadFileResponse(HttpBuffer.fromString("file contents"), "data.txt")));
    }
}
