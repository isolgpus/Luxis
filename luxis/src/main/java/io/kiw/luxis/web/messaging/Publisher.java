package io.kiw.luxis.web.messaging;

import io.vertx.core.Future;

import java.util.List;

public interface Publisher {

    /**
     * Publish a batch of events. Each {@link PendingOutboxEvent} carries the persisted outbox row
     * id alongside the event payload — implementations are encouraged to propagate that id as a
     * stable dedup key (e.g. a Kafka header) so consumers can drop duplicates produced by retries.
     * <p>
     * Treated as all-or-nothing: if the returned future fails, Luxis assumes none of the events
     * were delivered and the whole batch is retried on the next drain pass. Implementations should
     * fail the future for any partial failure.
     */
    Future<Void> publish(List<PendingOutboxEvent> events);
}
