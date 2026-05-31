package io.kiw.luxis.web.http.client;

public final class LuxisHttpClientConfig {

    private final String baseUrl;
    private final boolean errorAwareResponses;

    private LuxisHttpClientConfig(final String baseUrl, final boolean errorAwareResponses) {
        this.baseUrl = baseUrl;
        this.errorAwareResponses = errorAwareResponses;
    }

    public static LuxisHttpClientConfig defaults() {
        return new LuxisHttpClientConfig(null, false);
    }

    public LuxisHttpClientConfig baseUrl(final String baseUrl) {
        return new LuxisHttpClientConfig(baseUrl, this.errorAwareResponses);
    }

    public LuxisHttpClientConfig errorAwareResponses(final boolean errorAwareResponses) {
        return new LuxisHttpClientConfig(this.baseUrl, errorAwareResponses);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isErrorAwareResponses() {
        return errorAwareResponses;
    }
}
