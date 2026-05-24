package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.WebSocketRouteConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.DisconnectSession;
import io.kiw.luxis.web.pipeline.JustSendValidationError;
import io.kiw.luxis.web.pipeline.SendErrorResponse;
import io.kiw.luxis.web.pipeline.SendValidationErrorsAndDisconnectSession;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.websocket.WebSocketConnection;
import io.kiw.luxis.web.websocket.WebSocketMessage;
import io.kiw.luxis.web.websocket.WebSocketResponseMessage;
import io.kiw.luxis.web.websocket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HttpWebSocketRouteHandlerImpl<APP, RESP> implements HttpWebSocketRouteHandler {

    private final WebSocketRoutes<APP, RESP> route;
    private final ObjectMapper objectMapper;
    private final APP appState;
    private final Consumer<Exception> exceptionHandler;
    private final LuxisPipelineExecutor<WebSocketSession<?>> executor;
    private final WebSocketRoutesRegister<APP, RESP> routesRegister;
    private final LinkedHashMap<String, WebSocketRoute<?>> routes;
    private final Map<Class<?>, String> responseTypeRegistry;
    private final WebSocketRouteConfig config;

    public HttpWebSocketRouteHandlerImpl(final WebSocketRoutes<APP, RESP> route, final ObjectMapper objectMapper, final APP appState, final Consumer<Exception> exceptionHandler, final ExecutionDispatcher executionDispatcher, final WebSocketRouteConfig config, final PendingAsyncResponses pendingAsyncResponses, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.route = route;
        this.objectMapper = objectMapper;
        this.appState = appState;
        this.exceptionHandler = exceptionHandler;
        this.config = config;
        this.responseTypeRegistry = new HashMap<>();
        routes = new LinkedHashMap<>();
        final MessagingComponents resolvedMessaging = messaging != null ? messaging : MessagingComponents.NONE;
        routesRegister = new WebSocketRoutesRegister<>(appState, pendingAsyncResponses, routes, responseTypeRegistry, databaseClient, resolvedMessaging);
        route.registerRoutes(routesRegister);
        this.executor = new LuxisPipelineExecutor<>(appState, exceptionHandler, executionDispatcher, pendingAsyncResponses, new LuxisPipelineHandler<>() {
            @Override
            public void handleFailure(final WebSocketSession<?> session, final MapInstruction<?, ?, ?, ?, ?> instruction, final ErrorMessageResponse error) {
                if (instruction.isValidation()) {
                    switch (config.failedValidationStrategy()) {
                        case JustSendValidationError ignored -> sendErrorEnvelope(session, error);
                        case SendValidationErrorsAndDisconnectSession ignored ->
                                sendErrorEnvelope(session, error).thenAccept(result -> session.close());
                        case DisconnectSession ignored -> session.close();
                    }
                } else {
                    sendErrorEnvelope(session, error);
                }
            }

            @Override
            public void sendFinalResponse(final WebSocketSession<?> session, final Object result) {
                sendFinalEnvelope(session, result);
            }
        }, databaseClient, resolvedMessaging);
    }

    @Override
    public WebSocketSession<?> createSession(final WebSocketConnection connection) {
        return new WebSocketSession<>(connection, objectMapper, responseTypeRegistry, config.backpressureStrategy());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onOpen(final WebSocketSession<?> session) {
        try {
            route.onOpen((WebSocketSession<RESP>) session, appState);
        } catch (final Exception e) {
            exceptionHandler.accept(e);
        }
    }

    @Override
    public void onMessage(final String rawMessage, final WebSocketSession<?> session) {
        try {
            final WebSocketMessage envelope = objectMapper.readValue(rawMessage, WebSocketMessage.class);

            if (envelope.type() == null) {
                handleCorruptInput(session);
                return;
            }


            final WebSocketRoute<?> branch = routes.get(envelope.type());
            if (branch == null) {
                handleCorruptInput(session);
                return;
            }

            final Object payload = objectMapper.treeToValue(envelope.payload(), branch.messageType());
            executor.execute(session, branch.pipeline(), payload);

        } catch (final Exception e) {
            handleCorruptInput(session);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onClose(final WebSocketSession<?> session) {
        try {
            route.onClose((WebSocketSession<RESP>) session, appState);
        } catch (final Exception e) {
            exceptionHandler.accept(e);
        }
    }

    private void handleCorruptInput(final WebSocketSession<?> session) {
        switch (config.corruptInputStrategy()) {
            case DisconnectSession ignored -> session.close();
            case SendErrorResponse sendErrorResponse -> session.sendRaw(sendErrorResponse.message());
        }
    }

    private CompletableFuture<Void> sendErrorEnvelope(final WebSocketSession<?> session, final ErrorMessageResponse error) {
        final WebSocketResponseMessage envelope = new WebSocketResponseMessage("error", error);
        return session.sendRaw(objectMapper.writeValueAsString(envelope));
    }

    private void sendFinalEnvelope(final WebSocketSession<?> session, final Object result) {
        final String typeKey = responseTypeRegistry.get(result.getClass());
        if (typeKey == null) {
            throw new IllegalArgumentException("Unregistered response type: " + result.getClass().getName());
        }
        final WebSocketResponseMessage envelope = new WebSocketResponseMessage(typeKey, result);
        session.sendRaw(objectMapper.writeValueAsString(envelope));
    }
}
