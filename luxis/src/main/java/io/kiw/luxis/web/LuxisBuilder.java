package io.kiw.luxis.web;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.internal.EventConsumerHandler;
import io.kiw.luxis.web.internal.MessagingComponents;
import io.kiw.luxis.web.internal.OutboxDrainer;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.VertxExecutionDispatcher;
import io.kiw.luxis.web.internal.VertxRoutesRegistrar;
import io.kiw.luxis.web.internal.VertxTimeoutScheduler;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public final class LuxisBuilder<APP> {

    private final ApplicationRoutesRegister<APP> routes;
    private WebServerConfig webServerConfig;
    private DatabaseClient<?, ?, ?> databaseClient;
    private EventPlatform eventPlatform;

    LuxisBuilder(final ApplicationRoutesRegister<APP> routes) {
        this.routes = routes;
    }

    public LuxisBuilder<APP> withConfig(final WebServerConfig webServerConfig) {
        this.webServerConfig = webServerConfig;
        return this;
    }

    public LuxisBuilder<APP> withDatabase(final DatabaseClient<?, ?, ?> databaseClient) {
        this.databaseClient = databaseClient;
        return this;
    }

    // A Luxis instance supports a single EventConsumer (one topic) by design — event ordering across multiple consumers would not be deterministic.
    public LuxisBuilder<APP> withEventPlatform(final EventPlatform eventPlatform) {
        this.eventPlatform = eventPlatform;
        return this;
    }

    public Luxis<APP> start() {
        final WebServerConfig config = configOrDefault();
        final Publisher publisher = eventPlatform == null ? null : eventPlatform.publisher();
        final OutboxStore<?> outboxStore = eventPlatform == null ? null : eventPlatform.outboxStore();
        final EventConsumer eventConsumer = eventPlatform == null ? null : eventPlatform.eventConsumer();

        final Vertx vertx = Vertx.vertx();
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);

        final VertxExecutionDispatcher executionDispatcher = new VertxExecutionDispatcher(vertx);
        final VertxTimeoutScheduler timeoutScheduler = new VertxTimeoutScheduler(vertx);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(timeoutScheduler, config.exceptionHandler);

        final OutboxDrainer drainer = new OutboxDrainer(vertx, publisher, outboxStore,
                err -> config.exceptionHandler.accept(err instanceof Exception ? (Exception) err : new RuntimeException(err)));
        final MessagingComponents messaging = MessagingComponents.of(publisher, outboxStore, drainer);

        final VertxRoutesRegistrar.Registration<APP> registration = VertxRoutesRegistrar.registerWithEvents(
                router, routes, config.defaultTimeoutMillis, config.exceptionHandler,
                config.maxBodySize, config.corsConfig, executionDispatcher, pendingAsyncResponses,
                databaseClient, messaging, eventConsumer);
        final APP applicationState = registration.applicationState();
        final EventConsumerHandler eventHandler = registration.eventConsumerHandler();

        drainer.start();
        if (eventHandler != null) {
            eventHandler.start();
        }

        httpServer.requestHandler(router).listen(config.port).toCompletionStage().toCompletableFuture().join();
        return new VertxLuxis<>(vertx, executionDispatcher, applicationState, pendingAsyncResponses, () -> {
            if (eventHandler != null) {
                eventHandler.close();
            }
            drainer.stop();
            vertx.close().toCompletionStage().toCompletableFuture().join();
        });
    }

    public ApplicationRoutesRegister<APP> getRoutes() {
        return routes;
    }

    public DatabaseClient<?, ?, ?> getDatabaseClient() {
        return databaseClient;
    }

    public EventPlatform getEventPlatform() {
        return eventPlatform;
    }

    public WebServerConfig getConfig() {
        return configOrDefault();
    }

    private WebServerConfig configOrDefault() {
        return webServerConfig != null ? webServerConfig : new WebServiceConfigBuilder().build();
    }
}
