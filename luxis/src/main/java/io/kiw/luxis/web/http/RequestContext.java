package io.kiw.luxis.web.http;

import java.util.Map;

public interface RequestContext {
    HttpCookie getRequestCookie(final String key);

    String getQueryParam(final String key);

    void addResponseHeader(final String key, final String value);

    void addResponseCookie(final HttpCookie value);

    String getRequestBody();

    void setStatusCode(final int statusCode);

    void end(final String bodyResponse);

    void end(final HttpBuffer bodyResponse);

    String getRequestHeader(final String key);

    void next();

    void put(final String key, final Object successValue);

    Object get(final String key);

    boolean hasEnded();

    Map<String, HttpBuffer> resolveUploadedFiles();

    String getPathParam(final String key);

    void runOnContext(final Runnable task);
}
