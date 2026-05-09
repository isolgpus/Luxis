package io.kiw.luxis.web.internal;

public class BlockingRouteContext<IN, SESSION> {
    private final IN in;
    private final SESSION session;

    public BlockingRouteContext(final IN in, final SESSION session) {
        this.in = in;
        this.session = session;
    }

    public IN in() {
        return in;
    }

    public SESSION session() {
        return session;
    }
}
