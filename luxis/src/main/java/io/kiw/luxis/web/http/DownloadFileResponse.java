package io.kiw.luxis.web.http;

public record DownloadFileResponse(HttpBuffer fileContents, String fileName) {
}
