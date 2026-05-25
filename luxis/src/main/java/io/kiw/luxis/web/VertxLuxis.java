package io.kiw.luxis.web;

import io.kiw.luxis.web.internal.VertxExecutionDispatcher;
import io.vertx.core.Vertx;

import java.util.function.BiConsumer;

public class VertxLuxis<APP> implements Luxis<APP> {
    private final Vertx vertx;
    private final VertxExecutionDispatcher executionDispatcher;
    private final APP applicationState;
    private final AutoCloseable onClose;

    public VertxLuxis(final Vertx vertx, final VertxExecutionDispatcher executionDispatcher, final APP applicationState, final AutoCloseable onClose) {
        this.vertx = vertx;
        this.executionDispatcher = executionDispatcher;
        this.applicationState = applicationState;
        this.onClose = onClose;
    }

    @Override
    public Vertx getVertx() {
        return vertx;
    }


    @Override
    public <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer) {
        executionDispatcher.handleOnApplicationContext(() -> applicationStateConsumer.accept(immutableState, applicationState));
    }

    @Override
    public void close() throws Exception {
        onClose.close();
    }
}
