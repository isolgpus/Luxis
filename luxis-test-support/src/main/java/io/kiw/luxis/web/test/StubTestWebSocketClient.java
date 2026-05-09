package io.kiw.luxis.web.test;

import io.kiw.luxis.web.internal.HttpWebSocketRouteHandler;
import io.kiw.luxis.web.test.internal.StubWebSocketConnection;
import io.kiw.luxis.web.websocket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StubTestWebSocketClient implements TestWebSocketClient {

    private final List<String> receivedMessages = new ArrayList<>();
    private final HttpWebSocketRouteHandler handler;
    private final WebSocketSession<?> session;
    private final StubWebSocketConnection connection;

    public StubTestWebSocketClient(final HttpWebSocketRouteHandler handler) {
        this.handler = handler;
        this.connection = new StubWebSocketConnection(receivedMessages);
        this.session = handler.createSession(connection);
        handler.onOpen(session);
    }

    @Override
    public void send(final String jsonMessage) {
        handler.onMessage(jsonMessage, session);
    }


    @Override
    public void onResponses(final Consumer<List<String>> receivedMessageConsumer) {
        receivedMessageConsumer.accept(receivedMessages);
        receivedMessages.clear();
    }

    @Override
    public void close() {
        handler.onClose(session);
        connection.close();
    }

    @Override
    public boolean isClosed() {
        return connection.isClosed();
    }
}
