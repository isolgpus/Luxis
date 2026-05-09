package io.kiw.luxis.web.internal;

public class RouteContext<IN, APP, SESSION> {
    private final IN in;
    private final APP app;
    private final SESSION session;

    public RouteContext(final IN in, final SESSION session, final APP app) {
        this.in = in;
        this.app = app;
        this.session = session;
    }

    public IN in() {
        return in;
    }

    public APP app() {
        return app;
    }

    public SESSION session() {
        return session;
    }
}
