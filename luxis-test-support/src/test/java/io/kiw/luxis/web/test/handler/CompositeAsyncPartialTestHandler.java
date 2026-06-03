package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.client.CompositeLuxisAsync;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.Map;

public class CompositeAsyncPartialTestHandler implements JsonHandler<AsyncMapRequest, AsyncMapResponse, MyApplicationState> {

    @Override
    public LuxisPipeline<AsyncMapResponse> handle(final HttpStream<AsyncMapRequest, MyApplicationState> httpStream) {
        return httpStream
                .<Map<String, Result<HttpErrorResponse, Object>>>asyncMap(ctx -> CompositeLuxisAsync.<HttpErrorResponse>create()
                        .add("ok", AsyncTestSupport.<Integer, HttpErrorResponse>completed(ctx.in().value))
                        .add("bad", AsyncTestSupport.<Integer, HttpErrorResponse>failed(
                                new HttpErrorResponse(new ErrorMessageResponse("composite failure"), 500)))
                        .combine())
                .map(ctx -> {
                    final Map<String, Result<HttpErrorResponse, Object>> results = ctx.in();
                    // accept partial: fall back to 0 for any operation that failed
                    final int ok = results.get("ok").fold(failure -> 0, success -> (Integer) success);
                    final int bad = results.get("bad").fold(failure -> 0, success -> (Integer) success);
                    return new AsyncMapResponse(ok + bad);
                })
                .complete(ctx -> HttpResult.success(ctx.in()));
    }
}
