package io.kiw.luxis.web.pipeline;

public class AsyncMapConfig {

    private static final AsyncMapConfig DEFAULT = new AsyncMapConfigBuilder().build();

    final long timeoutMillis;
    final int maxRetries;
    final long retryIntervalMillis;

    AsyncMapConfig(final long timeoutMillis, final int maxRetries, final long retryIntervalMillis) {
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.retryIntervalMillis = retryIntervalMillis;
    }

    static AsyncMapConfig defaultConfig() {
        return DEFAULT;
    }
}
