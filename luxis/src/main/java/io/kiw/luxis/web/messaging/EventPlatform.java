package io.kiw.luxis.web.messaging;

import java.util.Objects;

public final class EventPlatform {

    private final Publisher publisher;
    private final OutboxStore<?> outboxStore;
    private final EventConsumer eventConsumer;

    private EventPlatform(final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        this.publisher = publisher;
        this.outboxStore = outboxStore;
        this.eventConsumer = eventConsumer;
    }

    public static EventPlatform of(final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(outboxStore, "outboxStore");
        Objects.requireNonNull(eventConsumer, "eventConsumer");
        return new EventPlatform(publisher, outboxStore, eventConsumer);
    }

    public Publisher publisher() {
        return publisher;
    }

    public OutboxStore<?> outboxStore() {
        return outboxStore;
    }

    public EventConsumer eventConsumer() {
        return eventConsumer;
    }
}
