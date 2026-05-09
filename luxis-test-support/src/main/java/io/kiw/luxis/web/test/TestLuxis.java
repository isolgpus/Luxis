package io.kiw.luxis.web.test;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.LuxisBuilder;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.internal.EventConsumerHandler;
import io.kiw.luxis.web.internal.MessagingComponents;
import io.kiw.luxis.web.internal.OutboxDrainer;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.RoutesRegister;
import io.kiw.luxis.web.internal.TransactionExecutor;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.kiw.luxis.web.test.internal.StubExecutionDispatcher;
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



    @SuppressWarnings("unchecked")
    public static <APP> TestLuxis<APP> from(final LuxisBuilder<APP> builder) {
        final WebServerConfig config = builder.getConfig();
        final EventPlatform eventPlatform = builder.getEventPlatform();
        final DatabaseClient<?, ?, ?> databaseClient = builder.getDatabaseClient();
        final Publisher publisher = eventPlatform == null ? null : eventPlatform.publisher();
        final OutboxStore<?> outboxStore = eventPlatform == null ? null : eventPlatform.outboxStore();
        final EventConsumer eventConsumer = eventPlatform == null ? null : eventPlatform.eventConsumer();

        final Consumer<Exception>[] ref = new Consumer[] {config.exceptionHandler()};
        final TimeInjector timeInjector = new TimeInjector();

        final StubTimeoutScheduler stubTimeoutScheduler = new StubTimeoutScheduler(timeInjector);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(stubTimeoutScheduler, e -> ref[0].accept(e));
        final StubExecutionDispatcher executionDispatcher = new StubExecutionDispatcher();

        final OutboxDrainer drainer = new OutboxDrainer(null, publisher, outboxStore,
                err -> ref[0].accept(err instanceof Exception ? (Exception) err : new RuntimeException(err)));
        final MessagingComponents messaging = MessagingComponents.of(publisher, outboxStore, drainer);

        final TransactionExecutor transactionExecutor = databaseClient == null ? null : new TransactionExecutor(databaseClient, executionDispatcher, messaging);
        final StubRouter router = new StubRouter(e -> ref[0].accept(e), pendingAsyncResponses, transactionExecutor, databaseClient, messaging);
        config.corsConfig().ifPresent(router::configureCors);
        router.setMaxBodySize(config.maxBodySize());

        final RoutesRegister routesRegister = new RoutesRegister(router, executionDispatcher, pendingAsyncResponses, databaseClient, messaging);
        final APP applicationState = builder.getRoutes().registerRoutes(routesRegister);

        final EventConsumerHandler eventHandler = eventConsumer == null ? null : new EventConsumerHandler(
                eventConsumer, routesRegister.getEventRoutes(), e -> ref[0].accept(e),
                executionDispatcher, pendingAsyncResponses, databaseClient, messaging);
        if (eventHandler != null) {
            eventHandler.start();
        }

        return new TestLuxis<>(router, applicationState, ref, pendingAsyncResponses, stubTimeoutScheduler, timeInjector, eventHandler);
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
