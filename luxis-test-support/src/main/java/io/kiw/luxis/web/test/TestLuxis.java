package io.kiw.luxis.web.test;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.LuxisBuilder;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.db.DatabaseClient;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestLuxis<APP> implements Luxis<APP> {

    private final StubRouter router;
    private final APP applicationState;
    private final List<Exception> seenExceptions;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final StubTimeoutScheduler stubTimeoutScheduler;
    private final TimeInjector timeInjector;
    private final EventConsumerHandler eventConsumerHandler;
    private volatile Vertx vertx;

    TestLuxis(final StubRouter router, final APP applicationState, final List<Exception> seenExceptions, final PendingAsyncResponses pendingAsyncResponses, final StubTimeoutScheduler stubTimeoutScheduler, final TimeInjector timeInjector, final EventConsumerHandler eventConsumerHandler) {
        this.router = router;
        this.applicationState = applicationState;
        this.seenExceptions = seenExceptions;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.stubTimeoutScheduler = stubTimeoutScheduler;
        this.timeInjector = timeInjector;
        this.eventConsumerHandler = eventConsumerHandler;
    }


    public static <APP> TestLuxis<APP> from(final LuxisBuilder<APP> builder, final StubNetwork network) {
        final WebServerConfig config = builder.getConfig();
        final EventPlatform eventPlatform = builder.getEventPlatform();
        final DatabaseClient<?, ?, ?> databaseClient = builder.getDatabaseClient();
        final Publisher publisher = eventPlatform == null ? null : eventPlatform.publisher();
        final OutboxStore<?> outboxStore = eventPlatform == null ? null : eventPlatform.outboxStore();
        final EventConsumer eventConsumer = eventPlatform == null ? null : eventPlatform.eventConsumer();

        final List<Exception> seenExceptions = new ArrayList<>();
        final Consumer<Exception> userHandler = config.exceptionHandler();
        final Consumer<Exception> handler = e -> {
            seenExceptions.add(e);
            userHandler.accept(e);
        };
        final TimeInjector timeInjector = new TimeInjector();

        final StubTimeoutScheduler stubTimeoutScheduler = new StubTimeoutScheduler(timeInjector);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(stubTimeoutScheduler, handler);
        final StubExecutionDispatcher executionDispatcher = new StubExecutionDispatcher();

        final OutboxDrainer drainer = new OutboxDrainer(null, publisher, outboxStore,
                err -> handler.accept(err instanceof Exception ? (Exception) err : new RuntimeException(err)));
        final MessagingComponents messaging = MessagingComponents.of(publisher, outboxStore, drainer);

        final TransactionExecutor transactionExecutor = databaseClient == null ? null : new TransactionExecutor(databaseClient, executionDispatcher, messaging);
        final StubRouter router = new StubRouter(handler, pendingAsyncResponses, transactionExecutor, databaseClient, messaging);
        config.corsConfig().ifPresent(router::configureCors);
        router.setMaxBodySize(config.maxBodySize());

        final LinkedHashMap<String, RoutesRegister.EventRouteEntry> eventRoutes = new LinkedHashMap<>();
        final RoutesRegister routesRegister = new RoutesRegister(router, executionDispatcher, pendingAsyncResponses, databaseClient, messaging, eventRoutes);
        final APP applicationState = builder.getRoutes().registerRoutes(routesRegister);

        final EventConsumerHandler eventHandler = eventConsumer == null ? null : new EventConsumerHandler(
                eventConsumer, eventRoutes, handler,
                executionDispatcher, pendingAsyncResponses, databaseClient, messaging);
        if (eventHandler != null) {
            eventHandler.start();
        }

        final TestLuxis<APP> testLuxis = new TestLuxis<>(router, applicationState, seenExceptions, pendingAsyncResponses, stubTimeoutScheduler, timeInjector, eventHandler);
        if (network != null) {
            network.register(config.host(), config.port(), testLuxis);
        }
        return testLuxis;
    }

    StubRouter getRouter() {
        return router;
    }

    public void assertNoMoreExceptions() {
        if (!seenExceptions.isEmpty()) {
            throw new AssertionError("Expected to find no exceptions but found " + seenExceptions.stream()
                    .map(Throwable::getMessage).collect(Collectors.toList()));
        }
    }

    public void assertException(final String message) {
        final Iterator<Exception> iterator = seenExceptions.iterator();
        while (iterator.hasNext()) {
            final Exception exception = iterator.next();
            if (exception.getMessage().contains(message)) {
                iterator.remove();
                return;
            }
        }
        throw new AssertionError("Unable to find exception in seen exceptions " + seenExceptions.stream()
                .map(Throwable::getMessage).collect(Collectors.toList()));
    }

    public void advanceTimeBy(final long millis) {
        stubTimeoutScheduler.advanceBy(millis);
    }

    @Override
    public <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer) {
        applicationStateConsumer.accept(immutableState, applicationState);
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
