package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.test.TestLuxis;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.LuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.CompletableFuture;

public class AsyncTimeoutTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    private TestLuxis<?> testLuxis;

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Integer>asyncMap(ctx -> {
                    // Deliberately do NOT complete — simulate missing response
                    // Advance time past the 30s timeout to trigger the timeout handler
                    testLuxis.advanceTimeBy(30_001);
                    return new LuxisAsync<>(new CompletableFuture<Result<HttpErrorResponse, Integer>>());
                })
                .map(ctx -> new AsyncMapResponse(ctx.in()))
                .complete(ctx -> HttpResult.success(ctx.in()));
    }

    public void evillyReferenceLuxis(final TestLuxis<?> testLuxis) {
        this.testLuxis = testLuxis;
    }
}
