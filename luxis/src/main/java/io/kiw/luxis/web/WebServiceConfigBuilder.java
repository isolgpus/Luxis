package io.kiw.luxis.web;

import io.kiw.luxis.web.cors.CorsConfig;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

public class WebServiceConfigBuilder {
    private int port = 8080;
    private int defaultTimeoutMillis = 30_000;
    private Consumer<Exception> exceptionHandler = (e) -> {
    };
    private OptionalLong maxBodySize = OptionalLong.empty();
    private Optional<CorsConfig> corsConfig = Optional.empty();

    public WebServiceConfigBuilder setPort(final int port) {
        this.port = port;
        return this;
    }

    public WebServiceConfigBuilder setExceptionHandler(final Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public WebServiceConfigBuilder setCorsConfig(final CorsConfig corsConfig) {
        this.corsConfig = Optional.of(corsConfig);
        return this;
    }

    public WebServerConfig build() {
        return new WebServerConfig(port, defaultTimeoutMillis, exceptionHandler, maxBodySize, corsConfig);
    }

    public WebServiceConfigBuilder setDefaultBlockingTimeoutMillis(final int timeoutMillis) {
        this.defaultTimeoutMillis = timeoutMillis;
        return this;
    }

    public WebServiceConfigBuilder setMaxBodySize(final long maxBodySize) {
        this.maxBodySize = OptionalLong.of(maxBodySize);
        return this;
    }
}
