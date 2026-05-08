package io.kiw.luxis.web.test;

import io.kiw.luxis.web.messaging.OutboxEvent;
import io.kiw.luxis.web.messaging.PendingOutboxEvent;
import io.kiw.luxis.web.messaging.Publisher;
import io.vertx.core.Future;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryPublisher implements Publisher {

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private boolean shouldFail;

    public InMemoryPublisher failPublishes() {
        this.shouldFail = true;
        return this;
    }

    public List<String> events() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    @Override
    public Future<Void> publish(final List<PendingOutboxEvent> batch) {
        if (shouldFail) {
            return Future.failedFuture(new RuntimeException("publish failed"));
        }
        events.add("publishBatch:" + batch.size());
        for (final PendingOutboxEvent pe : batch) {
            events.add(formatLine(pe));
            InMemoryEventBus.publish(pe.event().key(), payloadAsBuffer(pe.event().payload()));
        }
        return Future.succeededFuture();
    }

    private static ByteBuffer payloadAsBuffer(final OutboxEvent.Payload payload) {
        return switch (payload) {
            case OutboxEvent.Payload.Str s -> ByteBuffer.wrap(s.value().getBytes(StandardCharsets.UTF_8));
            case OutboxEvent.Payload.Bytes b -> ByteBuffer.wrap(b.value());
            case OutboxEvent.Payload.Buf b -> b.value().duplicate();
        };
    }

    private static String formatLine(final PendingOutboxEvent pe) {
        final OutboxEvent event = pe.event();
        final long id = pe.id();
        return switch (event.payload()) {
            case OutboxEvent.Payload.Str s -> "publish:str:" + id + ":" + event.key() + ":" + s.value();
            case OutboxEvent.Payload.Bytes b ->
                    "publish:bytes:" + id + ":" + event.key() + ":" + new String(b.value(), StandardCharsets.UTF_8);
            case OutboxEvent.Payload.Buf b -> "publish:buf:" + id + ":" + event.key() + ":" + readBuffer(b.value());
        };
    }

    private static String readBuffer(final ByteBuffer source) {
        final byte[] bytes = new byte[source.remaining()];
        source.duplicate().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
