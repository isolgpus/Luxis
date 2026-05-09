package io.kiw.luxis.web.test;

import java.util.List;
import java.util.function.Consumer;

public interface TestWebSocketClient {
    void send(String jsonMessage);

    void onResponses(Consumer<List<String>> receivedMessageConsumer);

    void close();

    boolean isClosed();
}
