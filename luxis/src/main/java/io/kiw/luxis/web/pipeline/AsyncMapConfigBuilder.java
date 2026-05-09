package io.kiw.luxis.web.pipeline;

public class AsyncMapConfigBuilder {

    private long timeoutMillis = 30_000;
    private int maxRetries = 0;
    private long retryIntervalMillis = 0;

    public AsyncMapConfigBuilder setTimeoutMillis(final long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        return this;
    }

    public AsyncMapConfigBuilder retries(final int maxRetries, final long retryIntervalMillis) {
        this.maxRetries = maxRetries;
        this.retryIntervalMillis = retryIntervalMillis;
        return this;
    }

    public AsyncMapConfig build() {
        return new AsyncMapConfig(timeoutMillis, maxRetries, retryIntervalMillis);
    }
}
