package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.atomic.AtomicInteger;

import static io.kiw.luxis.web.http.HttpResult.success;

public class PeekTestHandler implements JsonHandler<BlockingRequest, BlockingTestResponse, MyApplicationState> {

    public final AtomicInteger peekCount = new AtomicInteger(0);
    public final AtomicInteger blockingPeekCount = new AtomicInteger(0);

    @Override
    public LuxisPipeline<BlockingTestResponse> handle(final HttpStream<BlockingRequest, MyApplicationState> httpStream) {
        return httpStream
                .map(ctx -> ctx.in().numberToMultiply)
                .peek(ctx -> {
                    peekCount.incrementAndGet();
                })
                .blockingPeek(ctx -> {
                    blockingPeekCount.incrementAndGet();
                })
                .map(ctx -> ctx.in() * 3)
                .complete(ctx -> success(new BlockingTestResponse(ctx.in())));
    }
}
