package io.kiw.luxis.web.test;

import io.kiw.luxis.web.http.HttpCookie;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TestHttpResponse {
    public final String responseBody;
    public int statusCode = 200;
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final Map<String, String> responseCookies = new LinkedHashMap<>();

    public TestHttpResponse(final String responseBody) {
        this.responseBody = responseBody;
    }

    public TestHttpResponse withStatusCode(final int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public static TestHttpResponse response(final String expectedResponseBody) {
        return response(expectedResponseBody, "application/json");
    }

    public static TestHttpResponse response(final String expectedResponseBody, final String contentType) {
        return new TestHttpResponse(expectedResponseBody)
                .withHeader("Content-Type", contentType);
    }


    @Override
    public String toString() {
        return "TestHttpResponse{" +
                "responseBody='" + responseBody + '\'' +
                ", statusCode=" + statusCode +
                ", responseHeaders=" + responseHeaders +
                ", responseCookies=" + responseCookies +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TestHttpResponse that = (TestHttpResponse) o;
        return statusCode == that.statusCode &&
                Objects.equals(responseBody, that.responseBody) &&
                Objects.equals(responseHeaders, that.responseHeaders) &&
                Objects.equals(responseCookies, that.responseCookies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseBody, statusCode, responseHeaders, responseCookies);
    }

    public String getHeader(final String key) {
        return responseHeaders.get(key);
    }

    public String getCookie(final String key) {
        return responseCookies.get(key);
    }

    public TestHttpResponse withHeader(final String key, final String value) {
        if (!key.equals("content-length") && !key.equals("set-cookie")) {
            this.responseHeaders.put(key, value);
        }
        return this;
    }

    public TestHttpResponse withCookie(final HttpCookie value) {
        this.responseCookies.put(value.name(), value.value());
        return this;
    }

    public TestHttpResponse withCookie(final String key, final String value) {
        this.responseCookies.put(key, value);
        return this;
    }
}
