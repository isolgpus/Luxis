package io.kiw.luxis.web.messaging;

import java.nio.ByteBuffer;

public interface EventConsumer extends AutoCloseable {

    String topic();

    String extractKey(ByteBuffer message);

    <T> T decode(ByteBuffer message, Class<T> type);

    void start(EventDispatcher dispatcher);

    @Override
    void close();
}
