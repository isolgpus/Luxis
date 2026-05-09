package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import static io.kiw.luxis.web.http.HttpResult.success;

public class HttpClientPostCallHandler implements JsonHandler<HttpClientPostRequest, HttpClientGetResponse, MyApplicationState> {

    private final LuxisHttpClient httpClient;
    private final String url;

    public HttpClientPostCallHandler(final LuxisHttpClient httpClient, String url) {
        this.httpClient = httpClient;
        this.url = url;
    }

    @Override
    public LuxisPipeline<HttpClientGetResponse> handle(final HttpStream<HttpClientPostRequest, MyApplicationState> httpStream) {
        return httpStream
                .asyncMap(ctx ->
                        httpClient.post("http://" + url + ctx.in().targetPath, ctx.in().forwardBody))
                .map(ctx -> new HttpClientGetResponse(ctx.in().statusCode(), ctx.in().body()))
                .complete(ctx -> success(ctx.in()));
    }
}
