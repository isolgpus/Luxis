package io.kiw.luxis.web.test;

import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventDispatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class InMemoryEventConsumer implements EventConsumer {

    private final String topic;
    private final ObjectMapper objectMapper;
    private Consumer<ByteBuffer> subscriber;

    public InMemoryEventConsumer(final String topic) {
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public String extractKey(final ByteBuffer message) {
        final JsonNode envelope = readEnvelope(message);
        final JsonNode type = envelope.get("type");
        if (type == null || type.isNull()) {
            throw new IllegalArgumentException("Missing 'type' field in event envelope");
        }
        return type.asText();
    }

    @Override
    public <T> T decode(final ByteBuffer message, final Class<T> type) {
        final JsonNode envelope = readEnvelope(message);
        final JsonNode payload = envelope.get("payload");
        if (payload == null) {
            throw new IllegalArgumentException("Missing 'payload' field in event envelope");
        }
        return objectMapper.treeToValue(payload, type);
    }

    private JsonNode readEnvelope(final ByteBuffer message) {
        final byte[] bytes = new byte[message.remaining()];
        message.duplicate().get(bytes);
        return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void start(final EventDispatcher dispatcher) {
        this.subscriber = dispatcher::dispatch;
        InMemoryEventBus.subscribe(topic, subscriber);
    }

    @Override
    public void close() {
        if (subscriber != null) {
            InMemoryEventBus.unsubscribe(topic, subscriber);
            subscriber = null;
        }
    }
}
