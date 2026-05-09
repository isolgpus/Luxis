package io.kiw.luxis.web.http;

import io.kiw.luxis.web.jwt.JwtClaims;

import java.util.Map;

public class HttpSession {
    public final RequestContext ctx;

    public HttpSession(final RequestContext ctx) {
        this.ctx = ctx;
    }

    public String getQueryParam(final String key) {
        return ctx.getQueryParam(key);
    }

    public String getRequestHeader(final String key) {
        return ctx.getRequestHeader(key);
    }

    public HttpCookie getRequestCookie(final String key) {
        return ctx.getRequestCookie(key);
    }

    public void addResponseHeader(final String key, final String value) {
        ctx.addResponseHeader(key, value);
    }

    public void addResponseCookie(final HttpCookie value) {
        ctx.addResponseCookie(value);
    }

    public Map<String, HttpBuffer> resolveUploadedFiles() {
        return this.ctx.resolveUploadedFiles();
    }

    public String getPathParam(final String key) {
        return ctx.getPathParam(key);
    }

    public JwtClaims getJwtClaims() {
        return (JwtClaims) ctx.get("__jwt_claims__");
    }
}
