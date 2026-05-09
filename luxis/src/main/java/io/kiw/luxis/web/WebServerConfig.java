package io.kiw.luxis.web;

import io.kiw.luxis.web.cors.CorsConfig;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

public class WebServerConfig {
    final int port;
    final int defaultTimeoutMillis;
    final Consumer<Exception> exceptionHandler;
    final OptionalLong maxBodySize;
    final Optional<CorsConfig> corsConfig;

    WebServerConfig(final int port, final int defaultTimeoutMillis, final Consumer<Exception> exceptionHandler, final OptionalLong maxBodySize, final Optional<CorsConfig> corsConfig) {

        this.port = port;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.exceptionHandler = exceptionHandler;
        this.maxBodySize = maxBodySize;
        this.corsConfig = corsConfig;
    }

    public int port() {
        return port;
    }

    public int defaultTimeoutMillis() {
        return defaultTimeoutMillis;
    }

    public Consumer<Exception> exceptionHandler() {
        return exceptionHandler;
    }

    public OptionalLong maxBodySize() {
        return maxBodySize;
    }

    public Optional<CorsConfig> corsConfig() {
        return corsConfig;
    }
}
