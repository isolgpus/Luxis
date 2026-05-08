package io.kiw.luxis.web;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.internal.EventConsumerHandler;
import io.kiw.luxis.web.internal.MessagingComponents;
import io.kiw.luxis.web.internal.OutboxDrainer;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.RoutesRegister;
import io.kiw.luxis.web.internal.TransactionExecutor;
import io.kiw.luxis.web.internal.VertxExecutionDispatcher;
import io.kiw.luxis.web.internal.VertxRoutesRegistrar;
import io.kiw.luxis.web.internal.VertxTimeoutScheduler;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.kiw.luxis.web.test.StubExecutionDispatcher;
import io.kiw.luxis.web.test.StubRouter;
import io.kiw.luxis.web.test.StubTimeoutScheduler;
import io.kiw.luxis.web.test.TimeInjector;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Luxis<APP> extends AutoCloseable {


    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer) {
        return start(routesRegisterConsumer, new WebServiceConfigBuilder().build());
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig) {
        return start(routesRegisterConsumer, webServerConfig, null, null, null);
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient) {
        return start(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, null, null);
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient) {
        return start(routesRegisterConsumer, webServerConfig, databaseClient, null, null);
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore) {
        return start(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, publisher, outboxStore, null);
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        return start(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, publisher, outboxStore, eventConsumer);
    }

    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore) {
        return start(routesRegisterConsumer, webServerConfig, databaseClient, publisher, outboxStore, null);
    }

    // A Luxis instance supports a single EventConsumer (one topic) by design — event ordering across multiple consumers would not be deterministic.
    static <APP> Luxis<APP> start(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        final Vertx vertx = Vertx.vertx();
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);

        final VertxExecutionDispatcher executionDispatcher = new VertxExecutionDispatcher(vertx);
        final VertxTimeoutScheduler timeoutScheduler = new VertxTimeoutScheduler(vertx);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(timeoutScheduler, webServerConfig.exceptionHandler);

        final OutboxDrainer drainer = new OutboxDrainer(vertx, publisher, outboxStore,
                err -> webServerConfig.exceptionHandler.accept(err instanceof Exception ? (Exception) err : new RuntimeException(err)));
        final MessagingComponents messaging = MessagingComponents.of(publisher, outboxStore, drainer);

        final VertxRoutesRegistrar.Registration<APP> registration = VertxRoutesRegistrar.registerWithEvents(
                router, routesRegisterConsumer, webServerConfig.defaultTimeoutMillis, webServerConfig.exceptionHandler,
                webServerConfig.maxBodySize, webServerConfig.corsConfig, executionDispatcher, pendingAsyncResponses,
                databaseClient, messaging, eventConsumer);
        final APP applicationState = registration.applicationState();
        final EventConsumerHandler eventHandler = registration.eventConsumerHandler();

        drainer.start();
        if (eventHandler != null) {
            eventHandler.start();
        }

        httpServer.requestHandler(router).listen(webServerConfig.port).toCompletionStage().toCompletableFuture().join();
        return new VertxLuxis<>(vertx, executionDispatcher, applicationState, pendingAsyncResponses, () -> {
            if (eventHandler != null) {
                eventHandler.close();
            }
            drainer.stop();
            vertx.close().toCompletionStage().toCompletableFuture().join();
        });
    }


    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer) {
        return test(routesRegisterConsumer, (DatabaseClient<?, ?, ?>) null);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient) {
        return test(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, null, null);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig) {
        return test(routesRegisterConsumer, webServerConfig, null, null, null);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient) {
        return test(routesRegisterConsumer, webServerConfig, databaseClient, null, null);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore) {
        return test(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, publisher, outboxStore, null);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        return test(routesRegisterConsumer, new WebServiceConfigBuilder().build(), databaseClient, publisher, outboxStore, eventConsumer);
    }

    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore) {
        return test(routesRegisterConsumer, webServerConfig, databaseClient, publisher, outboxStore, null);
    }

    @SuppressWarnings("unchecked")
    public static <APP> TestLuxis<APP> test(final ApplicationRoutesRegister<APP> routesRegisterConsumer, final WebServerConfig webServerConfig, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        final Consumer<Exception>[] ref = new Consumer[] {webServerConfig.exceptionHandler};
        final TimeInjector timeInjector = new TimeInjector();

        final StubTimeoutScheduler stubTimeoutScheduler = new StubTimeoutScheduler(timeInjector);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(stubTimeoutScheduler, e -> ref[0].accept(e));
        final StubExecutionDispatcher executionDispatcher = new StubExecutionDispatcher();

        final OutboxDrainer drainer = new OutboxDrainer(null, publisher, outboxStore,
                err -> ref[0].accept(err instanceof Exception ? (Exception) err : new RuntimeException(err)));
        final MessagingComponents messaging = MessagingComponents.of(publisher, outboxStore, drainer);

        final TransactionExecutor transactionExecutor = databaseClient == null ? null : new TransactionExecutor(databaseClient, executionDispatcher, messaging);
        final StubRouter router = new StubRouter(e -> ref[0].accept(e), pendingAsyncResponses, transactionExecutor, databaseClient, messaging);
        webServerConfig.corsConfig.ifPresent(router::configureCors);
        router.setMaxBodySize(webServerConfig.maxBodySize);

        final RoutesRegister routesRegister = new RoutesRegister(router, executionDispatcher, pendingAsyncResponses, databaseClient, messaging);
        final APP applicationState = routesRegisterConsumer.registerRoutes(routesRegister);

        final EventConsumerHandler eventHandler = eventConsumer == null ? null : new EventConsumerHandler(
                eventConsumer, routesRegister.getEventRoutes(), e -> ref[0].accept(e),
                executionDispatcher, pendingAsyncResponses, databaseClient, messaging);
        if (eventHandler != null) {
            eventHandler.start();
        }

        return new TestLuxis<>(router, applicationState, ref, pendingAsyncResponses, stubTimeoutScheduler, timeInjector, eventHandler);
    }

    <IN> void apply(final IN immutableState, final BiConsumer<IN, APP> applicationStateConsumer);

    <T> void handleAsyncResponse(long correlationId, Result<HttpErrorResponse, T> result);

    Vertx getVertx();

}
