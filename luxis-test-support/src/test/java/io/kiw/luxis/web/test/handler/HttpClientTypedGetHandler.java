package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class HttpClientTypedGetHandler implements JsonHandler<HttpClientGetRequest, SimpleValueResponse, MyApplicationState> {

    private final LuxisHttpClient httpClient;
    private final String baseUrl;

    public HttpClientTypedGetHandler(final LuxisHttpClient httpClient, final String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public LuxisPipeline<SimpleValueResponse> handle(final HttpStream<HttpClientGetRequest, MyApplicationState> httpStream) {
        return httpStream
                .asyncMap(ctx -> httpClient.get(this.baseUrl + ctx.in().targetPath, SimpleValueResponse.class))
                .map(ctx -> ctx.in().body())
                .complete(ctx -> success(ctx.in()));
    }
}
