package io.kiw.luxis.web.pipeline;

public class TransactionRouteContext<IN, APP, SESSION> {
    private final IN in;
    private final APP app;
    private final SESSION session;

    public TransactionRouteContext(final IN in, final APP app, final SESSION session) {
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
