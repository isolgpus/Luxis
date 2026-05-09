package io.kiw.luxis.web;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.vertx.core.Vertx;

import java.util.function.BiConsumer;

public interface Luxis<APP> extends AutoCloseable {

    static <APP> LuxisBuilder<APP> app(final ApplicationRoutesRegister<APP> routesRegisterConsumer) {
        return new LuxisBuilder<>(routesRegisterConsumer);
    }

    <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer);

    <T> void handleAsyncResponse(long correlationId, Result<HttpErrorResponse, T> result);

    Vertx getVertx();

}
