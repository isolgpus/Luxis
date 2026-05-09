package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.websocket.WebSocketConnection;
import io.vertx.core.http.WebSocket;

import java.util.concurrent.CompletableFuture;

public class VertxClientWebSocketConnection implements WebSocketConnection {

    private final WebSocket webSocket;

    public VertxClientWebSocketConnection(final WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public CompletableFuture<Void> sendText(final String text) {
        return webSocket.writeTextMessage(text).toCompletionStage().toCompletableFuture();
    }

    @Override
    public boolean writeQueueFull() {
        return webSocket.writeQueueFull();
    }

    @Override
    public void close() {
        webSocket.close();
    }
}
