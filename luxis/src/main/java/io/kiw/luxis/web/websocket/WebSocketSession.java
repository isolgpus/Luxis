package io.kiw.luxis.web.websocket;

import io.kiw.luxis.web.pipeline.BackpressureStrategy;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WebSocketSession<RESP> {

    private final WebSocketConnection connection;
    private final ObjectMapper objectMapper;
    private final Map<Class<?>, String> classToTypeKey;
    private final BackpressureStrategy backpressureStrategy;

    public WebSocketSession(final WebSocketConnection connection, final ObjectMapper objectMapper, final Map<Class<?>, String> classToTypeKey, final BackpressureStrategy backpressureStrategy) {
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.classToTypeKey = classToTypeKey;
        this.backpressureStrategy = backpressureStrategy;
    }

    public void send(final RESP message) {
        if (shouldDisconnectDueToBackpressure()) {
            connection.close();
            return;
        }
        final String typeKey = classToTypeKey.get(message.getClass());
        if (typeKey == null) {
            throw new IllegalArgumentException("Unregistered response type: " + message.getClass().getName());
        }
        final WebSocketResponseMessage envelope = new WebSocketResponseMessage(typeKey, message);
        connection.sendText(objectMapper.writeValueAsString(envelope));
    }

    public CompletableFuture<Void> sendRaw(final String message) {
        if (shouldDisconnectDueToBackpressure()) {
            connection.close();
            return CompletableFuture.completedFuture(null);
        }
        return connection.sendText(message);
    }

    public void close() {
        connection.close();
    }

    private boolean shouldDisconnectDueToBackpressure() {
        return backpressureStrategy == BackpressureStrategy.DISCONNECT_CLIENT
                && connection.writeQueueFull();
    }

}
