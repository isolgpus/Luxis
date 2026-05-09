package io.kiw.luxis.web.test;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;

public final class WebServerTestMain {
    private WebServerTestMain() {
    }

    public static void main(final String[] args) {

        final Luxis<MyApplicationState> luxis = Luxis.app(
                        routesRegister -> TestApplicationRoutes.registerRoutes(routesRegister, new MyApplicationState()))
                .withConfig(new WebServiceConfigBuilder()
                        .setPort(8080)
                        .setDefaultBlockingTimeoutMillis(5000)
                        .setExceptionHandler(Throwable::printStackTrace)
                        .setMaxBodySize(1_048_576)
                        .build())
                .start();

        luxis.apply(82876, (event, myApplicationState) -> myApplicationState.setLongValue(event));
    }
}
