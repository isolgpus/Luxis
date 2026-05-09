package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;

public record MessagingComponents(Publisher publisher, OutboxStore<?> outboxStore, OutboxDrainer drainer) {

    public static final MessagingComponents NONE = new MessagingComponents(null, null, null);

    public static MessagingComponents of(final Publisher publisher, final OutboxStore<?> outboxStore, final OutboxDrainer drainer) {
        if (publisher == null && outboxStore == null && drainer == null) {
            return NONE;
        }
        return new MessagingComponents(publisher, outboxStore, drainer);
    }
}
