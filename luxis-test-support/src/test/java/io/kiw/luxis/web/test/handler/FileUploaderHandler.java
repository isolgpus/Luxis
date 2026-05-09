package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.FileUploadRoute;
import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class FileUploaderHandler extends FileUploadRoute<FileUploadResponse, MyApplicationState> {
    @Override
    public LuxisPipeline<FileUploadResponse> handle(final HttpStream<Map<String, HttpBuffer>, MyApplicationState> e) {
        return e.complete(ctx -> {
            final Map<String, Integer> results = ctx.in().entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    me -> me.getValue().bytes().length,
                    (integer, integer2) -> integer,
                    LinkedHashMap::new));
            return HttpResult.success(new FileUploadResponse(results));
        });
    }
}
