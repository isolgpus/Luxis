package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.ApplicationRoutesRegister;
import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

public final class VertxRoutesRegistrar {
    private VertxRoutesRegistrar() {
    }


    public static <R> Registration<R> registerWithEvents(final Router router,
                                                         final ApplicationRoutesRegister<R> routesRegisterConsumer,
                                                         final int defaultTimeoutMillis,
                                                         final Consumer<Exception> exceptionHandler,
                                                         final OptionalLong maxBodySize,
                                                         final Optional<CorsConfig> corsConfig,
                                                         final VertxExecutionDispatcher executionDispatcher,
                                                         final PendingAsyncResponses pendingAsyncResponses,
                                                         final DatabaseClient<?, ?, ?> databaseClient,
                                                         final MessagingComponents messaging,
                                                         final EventConsumer eventConsumer) {
        final MessagingComponents resolved = messaging != null ? messaging : MessagingComponents.NONE;
        final TransactionExecutor transactionExecutor = databaseClient == null ? null : new TransactionExecutor(databaseClient, executionDispatcher, resolved);
        final VertxRouterWrapperImpl routerWrapper = new VertxRouterWrapperImpl(router, defaultTimeoutMillis, exceptionHandler, pendingAsyncResponses, transactionExecutor, databaseClient, resolved);
        corsConfig.ifPresent(routerWrapper::configureCors);

        final BodyHandler handler = BodyHandler.create()
                .setDeleteUploadedFilesOnEnd(true);
        maxBodySize.ifPresent(handler::setBodyLimit);
        router.route().handler(handler);

        final RoutesRegister routesRegister = new RoutesRegister(routerWrapper, executionDispatcher, pendingAsyncResponses, databaseClient, resolved);
        final R applicationState = routesRegisterConsumer.registerRoutes(routesRegister);

        final EventConsumerHandler eventHandler = eventConsumer == null ? null : new EventConsumerHandler(
                eventConsumer, routesRegister.getEventRoutes(), exceptionHandler,
                executionDispatcher, pendingAsyncResponses, databaseClient, resolved);
        return new Registration<>(applicationState, eventHandler);
    }

    public record Registration<R>(R applicationState, EventConsumerHandler eventConsumerHandler) {
    }

}
