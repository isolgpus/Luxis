package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.BackpressureStrategy;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.websocket.ClientWebSocketRoutes;
import io.kiw.luxis.web.websocket.WebSocketConnection;
import io.kiw.luxis.web.websocket.WebSocketMessage;
import io.kiw.luxis.web.websocket.WebSocketResponseMessage;
import io.kiw.luxis.web.websocket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientWebSocketHandler<APP, RESP> {

    private final ObjectMapper objectMapper;
    private final LuxisPipelineExecutor<WebSocketSession<?>> executor;
    private final LinkedHashMap<String, WebSocketRoute<?>> routes;
    private final Map<Class<?>, String> responseTypeRegistry;

    public ClientWebSocketHandler(final ClientWebSocketRoutes<APP, RESP> clientRoutes, final ObjectMapper objectMapper, final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final Consumer<Exception> exceptionHandler) {
        this.objectMapper = objectMapper;
        this.responseTypeRegistry = new HashMap<>();
        this.routes = new LinkedHashMap<>();
        final WebSocketRoutesRegister<APP, RESP> routesRegister = new WebSocketRoutesRegister<>(null, pendingAsyncResponses, routes, responseTypeRegistry);
        clientRoutes.registerRoutes(routesRegister);
        this.executor = new LuxisPipelineExecutor<>(null, exceptionHandler, executionDispatcher, pendingAsyncResponses, new LuxisPipelineHandler<>() {
            @Override
            public void handleFailure(final WebSocketSession<?> session, final MapInstruction<?, ?, ?, ?, ?> instruction, final ErrorMessageResponse error) {
                sendErrorEnvelope(session, error);
            }

            @Override
            public void sendFinalResponse(final WebSocketSession<?> session, final Object result) {
                sendFinalEnvelope(session, result);
            }
        });
    }

    public WebSocketSession<RESP> createSession(final WebSocketConnection connection) {
        return new WebSocketSession<>(connection, objectMapper, responseTypeRegistry, BackpressureStrategy.UNBOUNDED_BUFFER);
    }

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

    private void handleCorruptInput(final WebSocketSession<?> session) {
        session.close();
    }

    private void sendErrorEnvelope(final WebSocketSession<?> session, final ErrorMessageResponse error) {
        final WebSocketResponseMessage envelope = new WebSocketResponseMessage("error", error);
        session.sendRaw(objectMapper.writeValueAsString(envelope));
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
