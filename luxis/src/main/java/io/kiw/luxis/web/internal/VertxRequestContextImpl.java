package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.RequestContext;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public class VertxRequestContextImpl implements RequestContext {
    private final RoutingContext ctx;

    public VertxRequestContextImpl(final RoutingContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public HttpCookie getRequestCookie(final String key) {
        final Cookie cookie = this.ctx.request().getCookie(key);
        if (cookie == null) {
            return null;
        }
        return new HttpCookie(cookie.getName(), cookie.getValue());
    }

    @Override
    public String getQueryParam(final String key) {
        return this.ctx.request().getParam(key);
    }

    @Override
    public void addResponseHeader(final String key, final String value) {
        if (!hasEnded()) {
            this.ctx.response().putHeader(key, value);
        }
    }

    @Override
    public void addResponseCookie(final HttpCookie value) {
        if (!hasEnded()) {
            this.ctx.response().addCookie(Cookie.cookie(value.name(), value.value()));
        }
    }

    @Override
    public String getRequestBody() {
        return this.ctx.body().asString();
    }

    @Override
    public void setStatusCode(final int statusCode) {
        if (!hasEnded()) {
            this.ctx.response().setStatusCode(statusCode);
        }
    }

    @Override
    public void end(final String bodyResponse) {
        if (!hasEnded()) {
            this.ctx.end(bodyResponse);
        }
    }

    @Override
    public void end(final HttpBuffer bodyResponse) {
        if (!hasEnded()) {
            this.ctx.end(Buffer.buffer(bodyResponse.bytes()));
        }
    }

    @Override
    public String getRequestHeader(final String key) {
        return this.ctx.request().getHeader(key);
    }

    @Override
    public void next() {
        this.ctx.next();
    }

    @Override
    public void put(final String key, final Object successValue) {
        this.ctx.put(key, successValue);
    }

    @Override
    public Object get(final String key) {
        return this.ctx.get(key);
    }

    @Override
    public boolean hasEnded() {
        return (boolean) this.ctx.data().getOrDefault("CONTEXT_DEAD", false);
    }

    @Override
    public String getPathParam(final String key) {
        return this.ctx.pathParam(key);
    }

    @Override
    public void runOnContext(final Runnable task) {
        this.ctx.vertx().runOnContext(v -> task.run());
    }

    @Override
    public Map<String, HttpBuffer> resolveUploadedFiles() {
        return this.ctx.fileUploads().stream()
                .collect(Collectors.toMap(
                        FileUpload::fileName,
                        a -> new HttpBuffer(ctx.vertx().fileSystem().readFileBlocking(a.uploadedFileName()).getBytes()),
                        new BinaryOperator<HttpBuffer>() {
                            @Override
                            public HttpBuffer apply(final HttpBuffer a, final HttpBuffer b) {
                                return a;
                            }
                        },
                        LinkedHashMap::new));
    }
}
