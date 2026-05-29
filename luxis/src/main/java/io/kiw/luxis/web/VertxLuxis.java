package io.kiw.luxis.web;

import io.kiw.luxis.web.internal.VertxExecutionDispatcher;

import java.util.function.BiConsumer;

public class VertxLuxis<APP> implements Luxis<APP> {
    private final VertxExecutionDispatcher executionDispatcher;
    private final APP applicationState;
    private final AutoCloseable onClose;

    public VertxLuxis(final VertxExecutionDispatcher executionDispatcher, final APP applicationState, final AutoCloseable onClose) {
        this.executionDispatcher = executionDispatcher;
        this.applicationState = applicationState;
        this.onClose = onClose;
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
