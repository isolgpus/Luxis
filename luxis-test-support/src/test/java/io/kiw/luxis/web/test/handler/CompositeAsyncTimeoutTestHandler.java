package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.CompositeLuxisAsync;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.AsyncMapConfig;
import io.kiw.luxis.web.pipeline.AsyncMapConfigBuilder;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CompositeAsyncTimeoutTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Map<String, Result<HttpErrorResponse, Object>>>asyncMap(ctx -> CompositeLuxisAsync.<HttpErrorResponse>create()
                        .add("fast", AsyncTestSupport.<Integer, HttpErrorResponse>completed(ctx.in().value))
                        // a never-completing operation, capped at 20ms
                        .add("slow", new LuxisAsync<Integer, HttpErrorResponse>(new CompletableFuture<>()),
                                Duration.ofMillis(20),
                                new HttpErrorResponse(new ErrorMessageResponse("slow timed out"), 504))
                        .combine(), new AsyncMapConfigBuilder().setTimeoutMillis(10000).build())
                .flatMap(ctx -> {
                    final Map<String, Result<HttpErrorResponse, Object>> results = ctx.in();
                    // collapse: the timed-out "slow" entry is a Result.error that short-circuits here
                    return results.get("fast").flatMap(fast ->
                            results.get("slow").map(slow -> new AsyncMapResponse((Integer) fast + (Integer) slow)));
                })
                .complete(ctx -> HttpResult.success(ctx.in()));
    }
}
