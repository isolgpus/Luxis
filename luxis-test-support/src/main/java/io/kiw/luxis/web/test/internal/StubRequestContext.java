package io.kiw.luxis.web.test.internal;

import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.RequestContext;
import io.kiw.luxis.web.test.TestHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class StubRequestContext implements RequestContext {
    private String requestBody;
    private final Map<String, String> queryParams;
    private final Map<String, String> requestHeaders;
    private final Map<String, HttpCookie> requestCookies;
    private final Map<String, HttpBuffer> fileUploads;
    private Map<String, String> pathParams = new LinkedHashMap<>();
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final Map<String, HttpCookie> responseCookies = new LinkedHashMap<>();
    private String responseBody;
    private int statusCode = 200;
    private Map<String, Object> state = new LinkedHashMap<>();
    private boolean finished = false;

    public StubRequestContext(final String requestBody, final Map<String, String> queryParams, final Map<String, String> requestHeaders, final Map<String, HttpCookie> requestCookies, final Map<String, HttpBuffer> fileUploads) {
        this.requestBody = requestBody;
        this.queryParams = queryParams;
        this.requestHeaders = requestHeaders;
        this.requestCookies = requestCookies;
        this.fileUploads = fileUploads;
    }

    @Override
    public HttpCookie getRequestCookie(final String key) {
        return this.requestCookies.get(key);
    }

    @Override
    public String getQueryParam(final String key) {
        return this.queryParams.get(key);
    }

    @Override
    public void addResponseHeader(final String key, final String value) {
        this.responseHeaders.put(key, value);
    }

    @Override
    public void addResponseCookie(final HttpCookie value) {
        this.responseCookies.put(value.name(), value);
    }

    @Override
    public String getRequestBody() {
        return this.requestBody;
    }

    @Override
    public void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public void end(final String responseBody) {
        if (statusCode == 204) {
            this.responseBody = "";
        } else {
            this.responseBody = responseBody;
        }
        this.finished = true;
    }

    @Override
    public void end(final HttpBuffer bodyResponse) {
        if (statusCode == 204) {
            this.responseBody = "";
        } else {
            this.responseBody = bodyResponse.toString(StandardCharsets.UTF_8);
        }
        this.finished = true;
    }

    @Override
    public String getRequestHeader(final String key) {
        return this.requestHeaders.get(key);
    }

    @Override
    public void next() {
    }

    @Override
    public void put(final String key, final Object successValue) {
        this.state.put(key, successValue);
    }

    @Override
    public Object get(final String key) {
        return this.state.get(key);
    }

    @Override
    public boolean hasEnded() {
        return false;
    }

    @Override
    public String getPathParam(final String key) {
        return this.pathParams.get(key);
    }

    @Override
    public Map<String, HttpBuffer> resolveUploadedFiles() {
        return fileUploads;
    }

    public void setPathParams(final Map<String, String> pathParams) {
        this.pathParams = pathParams;
    }

    public TestHttpResponse getResponse() {
        final TestHttpResponse testHttpResponse = new TestHttpResponse(responseBody).withStatusCode(statusCode);
        for (final Map.Entry<String, String> header : this.responseHeaders.entrySet()) {
            testHttpResponse.withHeader(header.getKey(), header.getValue());
        }
        for (final Map.Entry<String, HttpCookie> cookie : this.responseCookies.entrySet()) {
            testHttpResponse.withCookie(cookie.getValue());
        }
        return testHttpResponse;
    }

    public boolean hasFinished() {
        return finished;
    }

    @Override
    public void runOnContext(final Runnable task) {
        task.run();
    }
}
