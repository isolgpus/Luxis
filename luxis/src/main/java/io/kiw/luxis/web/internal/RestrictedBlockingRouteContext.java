package io.kiw.luxis.web.internal;

public class RestrictedBlockingRouteContext<IN> {
    private final IN in;

    public RestrictedBlockingRouteContext(final IN in) {
        this.in = in;
    }

    public IN in() {
        return in;
    }
}
