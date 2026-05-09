package io.kiw.luxis.web.test;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.internal.EventConsumerHandler;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.test.internal.StubRouter;
import io.kiw.luxis.web.test.internal.StubTimeoutScheduler;
import io.vertx.core.Vertx;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TestLuxis<APP> implements Luxis<APP> {

    private final StubRouter router;
    private final APP applicationState;
    private final Consumer<Exception>[] exceptionHandlerRef;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final StubTimeoutScheduler stubTimeoutScheduler;
    private final TimeInjector timeInjector;
    private final EventConsumerHandler eventConsumerHandler;
    private volatile Vertx vertx;

    @SuppressWarnings("unchecked")
    TestLuxis(final StubRouter router, final APP applicationState, final Consumer<Exception>[] exceptionHandlerRef, final PendingAsyncResponses pendingAsyncResponses, final StubTimeoutScheduler stubTimeoutScheduler, final TimeInjector timeInjector) {
        this(router, applicationState, exceptionHandlerRef, pendingAsyncResponses, stubTimeoutScheduler, timeInjector, null);
    }

    @SuppressWarnings("unchecked")
    TestLuxis(final StubRouter router, final APP applicationState, final Consumer<Exception>[] exceptionHandlerRef, final PendingAsyncResponses pendingAsyncResponses, final StubTimeoutScheduler stubTimeoutScheduler, final TimeInjector timeInjector, final EventConsumerHandler eventConsumerHandler) {
        this.router = router;
        this.applicationState = applicationState;
        this.exceptionHandlerRef = exceptionHandlerRef;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.stubTimeoutScheduler = stubTimeoutScheduler;
        this.timeInjector = timeInjector;
        this.eventConsumerHandler = eventConsumerHandler;
    }


    public void setExceptionHandler(final Consumer<Exception> handler) {
        exceptionHandlerRef[0] = handler;
    }

    StubRouter getRouter() {
        return router;
    }

    public void advanceTimeBy(final long millis) {
        stubTimeoutScheduler.advanceBy(millis);
    }

    @Override
    public <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer) {
        applicationStateConsumer.accept(immutableState, applicationState);
    }

    @Override
    public <T> void handleAsyncResponse(final long correlationId, final Result<HttpErrorResponse, T> result) {
        pendingAsyncResponses.complete(correlationId, result);
    }


    @Override
    public synchronized Vertx getVertx() {
        if (vertx == null) {
            vertx = Vertx.vertx();
        }
        return vertx;
    }

    @Override
    public synchronized void close() {
        if (eventConsumerHandler != null) {
            eventConsumerHandler.close();
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
            vertx = null;
        }
    }

    public TimeInjector getTimeInjector() {
        return timeInjector;
    }
}
