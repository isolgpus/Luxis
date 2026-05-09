package io.kiw.luxis.web.http.client;

import io.kiw.luxis.web.http.HttpBuffer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpClientRequest {
    private final String url;
    private final Object body;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    private final Map<String, HttpBuffer> fileUploads = new LinkedHashMap<>();

    private HttpClientRequest(final String url, final Object body) {
        this.url = url;
        this.body = body;
    }

    public static HttpClientRequest request(final String url) {
        return request(url, null);
    }

    public static HttpClientRequest request(final String url, final Object body) {
        return new HttpClientRequest(url, body);
    }


    public HttpClientRequest header(final String key, final String value) {
        this.headers.put(key, value);
        return this;
    }

    public HttpClientRequest queryParam(final String key, final String value) {
        this.queryParams.put(key, value);
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Object getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public HttpClientRequest fileUpload(final String name, final HttpBuffer contents) {
        this.fileUploads.put(name, contents);
        return this;
    }

    public HttpClientRequest fileUpload(final String name, final String contents) {
        return fileUpload(name, HttpBuffer.fromString(contents));
    }

    public Map<String, HttpBuffer> getFileUploads() {
        return fileUploads;
    }
}
