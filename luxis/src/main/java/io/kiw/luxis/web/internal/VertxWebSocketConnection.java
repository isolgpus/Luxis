package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.websocket.WebSocketConnection;
import io.vertx.core.http.ServerWebSocket;

import java.util.concurrent.CompletableFuture;

public class VertxWebSocketConnection implements WebSocketConnection {

    private final ServerWebSocket webSocket;

    public VertxWebSocketConnection(final ServerWebSocket webSocket) {
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
