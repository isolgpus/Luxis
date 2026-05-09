package io.kiw.luxis.web.test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InMemoryEventBus {

    private static final Map<String, List<Consumer<ByteBuffer>>> SUBSCRIBERS = new ConcurrentHashMap<>();

    private InMemoryEventBus() {
    }

    public static void subscribe(final String topic, final Consumer<ByteBuffer> handler) {
        SUBSCRIBERS.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public static void unsubscribe(final String topic, final Consumer<ByteBuffer> handler) {
        final List<Consumer<ByteBuffer>> list = SUBSCRIBERS.get(topic);
        if (list != null) {
            list.remove(handler);
        }
    }

    public static void publish(final String topic, final ByteBuffer message) {
        final List<Consumer<ByteBuffer>> list = SUBSCRIBERS.get(topic);
        if (list == null) {
            return;
        }
        for (final Consumer<ByteBuffer> sub : list) {
            sub.accept(message.duplicate());
        }
    }
}
