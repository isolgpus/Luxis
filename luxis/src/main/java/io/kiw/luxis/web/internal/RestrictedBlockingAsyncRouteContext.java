package io.kiw.luxis.web.internal;

public final class RestrictedBlockingAsyncRouteContext<IN, ERR> extends RestrictedBlockingRouteContext<IN> {

    public RestrictedBlockingAsyncRouteContext(final IN in) {
        super(in);
    }
}
