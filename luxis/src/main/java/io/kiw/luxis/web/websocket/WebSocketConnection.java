package io.kiw.luxis.web.websocket;

import java.util.concurrent.CompletableFuture;

public interface WebSocketConnection {

    CompletableFuture<Void> sendText(String text);

    boolean writeQueueFull();

    void close();
}
