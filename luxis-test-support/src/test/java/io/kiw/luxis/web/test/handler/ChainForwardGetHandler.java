package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class ChainForwardGetHandler implements JsonHandler<Void, SimpleValueResponse, MyApplicationState> {

    private final LuxisHttpClient httpClient;
    private final String targetUrl;

    public ChainForwardGetHandler(final LuxisHttpClient httpClient, final String targetUrl) {
        this.httpClient = httpClient;
        this.targetUrl = targetUrl;
    }

    @Override
    public LuxisPipeline<SimpleValueResponse> handle(final HttpStream<Void, MyApplicationState> httpStream) {
        return httpStream
                .asyncMap(ctx -> httpClient.get(targetUrl, SimpleValueResponse.class))
                .map(ctx -> ctx.in().body())
                .complete(ctx -> success(ctx.in()));
    }
}
