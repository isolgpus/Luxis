package io.kiw.luxis.web.messaging;

public record PendingOutboxEvent(long id, OutboxEvent event) {
}
