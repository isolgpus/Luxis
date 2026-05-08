package io.kiw.luxis.web.messaging;

import java.nio.ByteBuffer;
import java.util.List;

public final class TransactionPublisher {

    private final List<OutboxEvent> buffer;

    public TransactionPublisher(final List<OutboxEvent> buffer) {
        this.buffer = buffer;
    }

    public void publish(final String key, final String message) {
        require();
        buffer.add(OutboxEvent.of(key, message));
    }

    public void publish(final String key, final byte[] message) {
        require();
        buffer.add(OutboxEvent.of(key, message));
    }

    public void publish(final String key, final ByteBuffer message) {
        require();
        buffer.add(OutboxEvent.of(key, message));
    }

    private void require() {
        if (buffer == null) {
            throw new IllegalStateException(
                    "Cannot publish from inside a transaction without an OutboxStore registered at Luxis.app(...).");
        }
    }
}
