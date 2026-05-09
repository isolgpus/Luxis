package io.kiw.luxis.web.http.client;

public final class LuxisHttpClientConfig {

    private final String baseUrl;
    private final boolean ssl;
    private final boolean errorAwareResponses;

    private LuxisHttpClientConfig(final String baseUrl, final boolean ssl, final boolean errorAwareResponses) {
        this.baseUrl = baseUrl;
        this.ssl = ssl;
        this.errorAwareResponses = errorAwareResponses;
    }

    public static LuxisHttpClientConfig defaults() {
        return new LuxisHttpClientConfig(null, false, false);
    }

    public LuxisHttpClientConfig baseUrl(final String baseUrl) {
        return new LuxisHttpClientConfig(baseUrl, this.ssl, this.errorAwareResponses);
    }

    public LuxisHttpClientConfig ssl(final boolean ssl) {
        return new LuxisHttpClientConfig(this.baseUrl, ssl, this.errorAwareResponses);
    }

    public LuxisHttpClientConfig errorAwareResponses(final boolean errorAwareResponses) {
        return new LuxisHttpClientConfig(this.baseUrl, this.ssl, errorAwareResponses);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isSsl() {
        return ssl;
    }

    public boolean isErrorAwareResponses() {
        return errorAwareResponses;
    }
}
