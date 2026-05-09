package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.RouteConfig;
import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.websocket.WebSocketSession;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.function.Consumer;

public class VertxRouterWrapperImpl extends RouterWrapper {
    private final Router router;
    private final int defaultTimeoutMillis;

    public VertxRouterWrapperImpl(final Router router, final int defaultTimeoutMillis, final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor) {
        this(router, defaultTimeoutMillis, exceptionHandler, pendingAsyncResponses, transactionExecutor, null, MessagingComponents.NONE);
    }

    public VertxRouterWrapperImpl(final Router router, final int defaultTimeoutMillis, final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor, final DatabaseClient<?, ?, ?> databaseClient) {
        this(router, defaultTimeoutMillis, exceptionHandler, pendingAsyncResponses, transactionExecutor, databaseClient, MessagingComponents.NONE);
    }

    public VertxRouterWrapperImpl(final Router router, final int defaultTimeoutMillis, final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        super(exceptionHandler, pendingAsyncResponses, transactionExecutor, databaseClient, messaging);
        this.router = router;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    @Override
    public void configureCors(final CorsConfig corsConfig) {
        final CorsHandler corsHandler = CorsHandler.create();
        for (final String origin : corsConfig.allowedOrigins()) {
            corsHandler.addOrigin(origin);
        }
        for (final String method : corsConfig.allowedMethods()) {
            corsHandler.allowedMethod(HttpMethod.valueOf(method));
        }
        if (!corsConfig.allowedHeaders().isEmpty()) {
            corsHandler.allowedHeaders(corsConfig.allowedHeaders());
        }
        if (!corsConfig.exposedHeaders().isEmpty()) {
            corsHandler.exposedHeaders(corsConfig.exposedHeaders());
        }
        corsHandler.allowCredentials(corsConfig.allowCredentials());
        if (corsConfig.maxAgeSeconds() >= 0) {
            corsHandler.maxAgeSeconds(corsConfig.maxAgeSeconds());
        }
        router.route().handler(corsHandler);
    }

    @Override
    public void route(final String path, final Method method, final String consumes, final String produces, final LuxisPipeline<?> flow, final RouteConfig routeConfig) {
        final Route route = router.route(HttpMethod.valueOf(method.name()), path).consumes(consumes).produces(produces);
        registerHandlers(route, flow, routeConfig);
    }

    @Override
    public void route(final String path, final String consumes, final String produces, final LuxisPipeline<?> flow, final RouteConfig routeConfig) {
        final Route route = router.route(path).consumes(consumes).produces(produces);
        registerHandlers(route, flow, routeConfig);
    }

    @Override
    protected void webSocketRoute(final String path, final HttpWebSocketRouteHandler handler) {
        router.route(path).handler(ctx -> {
            ctx.request().toWebSocket()
                    .onSuccess(ws -> {
                        final VertxWebSocketConnection connection = new VertxWebSocketConnection(ws);
                        final WebSocketSession<?> session = handler.createSession(connection);
                        handler.onOpen(session);
                        ws.textMessageHandler(msg -> handler.onMessage(msg, session));
                        ws.closeHandler(v -> handler.onClose(session));
                    })
                    .onFailure(err -> {
                        ctx.response().setStatusCode(500).end();
                    });
        });
    }

    private void registerHandlers(final Route route, final LuxisPipeline<?> flow, final RouteConfig routeConfig) {
        final int timeout = routeConfig.timeoutInMillis().orElse(defaultTimeoutMillis);

        route.handler(new VertxTimeoutHandler(timeout));

        for (final MapInstruction applicationInstruction : flow.getApplicationInstructions()) {
            if (applicationInstruction.isTransactional) {
                route.handler(ctx -> handleTransactional(applicationInstruction, new VertxRequestContextImpl(ctx), flow.getApplicationState(), flow.getEnder()));
            } else if (applicationInstruction.isAsync && applicationInstruction.isBlocking) {
                route.blockingHandler(ctx -> handleAsync(applicationInstruction, new VertxRequestContextImpl(ctx), flow.getApplicationState(), flow.getEnder()));
            } else if (applicationInstruction.isAsync) {
                route.handler(ctx -> handleAsync(applicationInstruction, new VertxRequestContextImpl(ctx), flow.getApplicationState(), flow.getEnder()));
            } else if (applicationInstruction.isBlocking) {
                route.blockingHandler(ctx -> handle(applicationInstruction, new VertxRequestContextImpl(ctx), null, flow.getEnder()));
            } else {
                route.handler(ctx -> handle(applicationInstruction, new VertxRequestContextImpl(ctx), flow.getApplicationState(), flow.getEnder()));
            }
        }
    }

}
