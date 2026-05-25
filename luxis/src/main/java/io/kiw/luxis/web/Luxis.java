package io.kiw.luxis.web;

import io.vertx.core.Vertx;

import java.util.function.BiConsumer;

public interface Luxis<APP> extends AutoCloseable {

    static <APP> LuxisBuilder<APP> app(final ApplicationRoutesRegister<APP> routesRegisterConsumer) {
        return new LuxisBuilder<>(routesRegisterConsumer);
    }

    <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer);

    Vertx getVertx();

}
