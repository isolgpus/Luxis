package io.kiw.luxis.web.test.handler;

import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.pipeline.LoopConfig;
import io.kiw.luxis.web.pipeline.LoopStep;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.MyApplicationState;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exercises {@link io.kiw.luxis.web.pipeline.LuxisStream#loop} over the WebSocket pipeline (which runs
 * through {@code LuxisPipelineExecutor}, not the HTTP {@code RouterWrapper}). Each iteration runs a
 * blocking step on a worker thread, then the loop exits once the running count reaches the requested
 * target.
 */
public class LoopWebSocketRoutes extends WebSocketRoutes<MyApplicationState, TestWebSocketResponse> {

    private final AtomicLong iterationCounter;

    public LoopWebSocketRoutes(final AtomicLong iterationCounter) {
        this.iterationCounter = iterationCounter;
    }

    @Override
    public void registerRoutes(final WebSocketRoutesRegister<MyApplicationState, TestWebSocketResponse> routesRegister) {
        routesRegister.registerOutbound("numberResponse", WebSocketNumberResponse.class);

        routesRegister.registerInbound("loop", WebSocketNumberRequest.class, s ->
                s.map(ctx -> new Counter(0, ctx.in().value))
                        .loop(LoopConfig.maxIterations(20), loop -> loop
                                .blockingMap(ctx -> {
                                    iterationCounter.incrementAndGet();
                                    return new AtomicReference<>(ctx.in().increment());
                                })
                                .asyncMap(ctx -> {
                                    return AsyncTestSupport.completed(ctx.in().get());
                                })
                                .until(c -> c.count() >= c.target()
                                        ? LoopStep.<Counter, WebSocketNumberResponse>done(new WebSocketNumberResponse(c.count()))
                                        : LoopStep.<Counter, WebSocketNumberResponse>again(c)))
                        .complete());
    }

    private record Counter(int count, int target) {
        Counter increment() {
            return new Counter(count + 1, target);
        }
    }
}
