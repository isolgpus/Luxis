package io.kiw.luxis.web.internal.ender;

import io.kiw.luxis.web.http.DownloadFileResponse;
import io.kiw.luxis.web.http.RequestContext;

public final class FileEnder implements Ender {

    @Override
    public void end(final RequestContext requestContext, final Object value) {
        final DownloadFileResponse value1 = (DownloadFileResponse) value;
        requestContext.addResponseHeader("Content-Disposition", value1.fileName());
        requestContext.end(value1.fileContents());
    }
}
