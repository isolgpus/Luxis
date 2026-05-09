package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.http.HttpBuffer;

public class FileUploadRequest {
    private final String name;
    private final HttpBuffer buffer;

    public FileUploadRequest(final String name, final HttpBuffer buffer) {
        this.name = name;
        this.buffer = buffer;
    }
}
