package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.websocket.WebSocketConnection;
import io.kiw.luxis.web.websocket.WebSocketSession;

public interface HttpWebSocketRouteHandler {
    WebSocketSession<?> createSession(WebSocketConnection connection);

    void onOpen(WebSocketSession<?> session);

    void onMessage(String rawMessage, WebSocketSession<?> session);

    void onClose(WebSocketSession<?> session);
}
