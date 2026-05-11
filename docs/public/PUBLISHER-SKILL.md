---
name: luxis-publisher
description: Use when implementing the Luxis `Publisher` SPI for a specific broker (Kafka, NATS, RabbitMQ, SNS, in-process, etc.). Triggers on imports of `io.kiw.luxis.web.messaging.Publisher`, `io.kiw.luxis.web.messaging.OutboxEvent`, scaffolding a `luxis-<broker>` library, or the user asking how to publish events from Luxis to a particular broker.
---

# Luxis Publisher Implementation

Luxis ships an event-outbox messaging layer (see https://isolgpus.github.io/Luxis/guides/messaging/). Inside a `inTransaction` sub-chain, events are buffered and atomically appended to a user-supplied outbox table at commit time. A Luxis-owned drainer reads pending batches and dispatches them via a `Publisher` you implement. This skill is the contract for implementing that `Publisher`.

## What you're implementing

```java
package io.kiw.luxis.web.messaging;

public interface Publisher {
    Future<Void> publish(List<PendingOutboxEvent> events);
}
```

`Future` is `io.vertx.core.Future`. One method. Called by the drainer once per batch, and by the in-pipeline `AsyncPublisher` once per immediate (non-transactional) `publish(...)` call wrapped in a single-element list. Each `PendingOutboxEvent` is `(long id, OutboxEvent event)` — `id` is the persisted outbox row id, `event` carries the `key` and sealed `payload`.

## The contract — non-negotiable

These rules are how Luxis's at-least-once guarantee and dedup story stay sound. Break them and consumers will see silent drops or undeduplicable duplicates.

- **All-or-nothing.** If *any* event in the batch fails, fail the returned future. Do not partially succeed; do not swallow errors. Luxis retries the whole batch on the next drain pass — partial success would produce duplicates that consumers can't dedupe by row id.
- **`OutboxEvent.key` is the destination** — topic / channel / queue / subject / whatever your broker calls "where this event goes". Don't repurpose it as a partition key or message key. If you need a partition key, take it from a header you stamp at publish time, derive it deterministically from the payload, or leave it null.
- **`OutboxEvent.payload` is sealed** with three variants — `Str`, `Bytes`, `Buf` (`ByteBuffer`). Switch on the variant. Don't drop any variant. Broker libs that only accept `byte[]` should still handle all three (UTF-8 encode `Str`, copy out of the `ByteBuffer` for `Buf`).
- **Threading.** `publish` is called from a Vert.x event-loop context (the drainer's). Return a `Future` that completes on a Vert.x-friendly thread. If your client lib uses its own threads, hop back via a `Promise<Void>` resolved from `vertx.runOnContext(...)`, or use whatever Vert.x adapter your client provides.
- **No blocking on the calling thread.** Return immediately with an unresolved `Future`. No `.get()`, no `.join()`, no `Thread.sleep`, no synchronous I/O on the event loop.
- **No retry loops inside `publish`.** Luxis owns retries via the next drain pass. Adding internal retries means duplicating Luxis's behaviour and burning the event-loop thread.
- **No cross-batch state that affects ordering or dedup.** The drainer may pipeline batches if `pollIntervalMillis` is small. Treat each batch as independent.
- **Idempotency is the consumer's problem.** Luxis is at-least-once. The drainer can re-publish after a crash between `publish` and `markBatchSent`. Document the dedup story for your broker — usually "stamp the outbox row id as a header / message key prefix, consumers dedupe by it."

## Skeleton implementation

```java
package com.example.luxis.broker;

import io.kiw.luxis.web.messaging.OutboxEvent;
import io.kiw.luxis.web.messaging.PendingOutboxEvent;
import io.kiw.luxis.web.messaging.Publisher;
import io.vertx.core.Future;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MyBrokerPublisher implements Publisher, AutoCloseable {

    private final MyBrokerClient client; // your client lib

    public MyBrokerPublisher(final MyBrokerConfig config) {
        this.client = MyBrokerClient.create(config);
    }

    @Override
    public Future<Void> publish(final List<PendingOutboxEvent> events) {
        if (events.isEmpty()) {
            return Future.succeededFuture();
        }

        final List<Future<?>> sends = new ArrayList<>(events.size());
        for (final PendingOutboxEvent pending : events) {
            sends.add(sendOne(pending));
        }
        return Future.all(sends).mapEmpty();
    }

    private Future<?> sendOne(final PendingOutboxEvent pending) {
        final OutboxEvent event = pending.event();
        final byte[] payload = switch (event.payload()) {
            case OutboxEvent.Payload.Str s   -> s.value().getBytes(StandardCharsets.UTF_8);
            case OutboxEvent.Payload.Bytes b -> b.value();
            case OutboxEvent.Payload.Buf b   -> readBuf(b.value());
        };
        // event.key() is the destination — topic / channel / queue / subject.
        // pending.id() is the persisted outbox row id — propagate it as a header / message key prefix for consumer dedup.
        return client.send(event.key(), payload, pending.id());
    }

    private static byte[] readBuf(final ByteBuffer buf) {
        final byte[] out = new byte[buf.remaining()];
        buf.duplicate().get(out);
        return out;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
```

`Future.all(...)` short-circuits to a failed composite the moment any individual future fails — you get all-or-nothing for free, provided each `client.send(...)` call returns a Vert.x `Future`. If your client returns `CompletableFuture` or callbacks, bridge it inside `sendOne` using `Promise<Void>` and resolve the promise from the callback / completion stage.

## Wiring it into Luxis

```java
final Publisher publisher = new MyBrokerPublisher(config);
final OutboxStore<MyTx> outbox = new MyOutboxStore();

Luxis.app(routes)
    .withConfig(webConfig)
    .withDatabase(databaseClient)
    .withEventPlatform(EventPlatform.of(publisher, outbox, eventConsumer))
    .start();
```

The user provides `OutboxStore` (the persistence side). You provide `Publisher` (the wire side). Both are registered together via `EventPlatform`.

## Testing

Three layers, in order of effort:

1. **Unit-test the payload switch.** Pass an `OutboxEvent` of each variant (`Str`, `Bytes`, `Buf`) with a fake/mocked client and assert all three reach the wire with the correct bytes.
2. **Unit-test the all-or-nothing semantics.** Stub the client so the second `send` in a batch fails. Assert the future returned from `publish(...)` is failed and the cause is propagated.
3. **Round-trip test with `Luxis.app(...).test()`.** Use `InMemoryDatabaseClient` + a real `OutboxStore` impl + your `Publisher` against an embedded broker or test container. Hit a route that uses `ctx.publisher().publish(...)` inside a transaction, assert the broker received the event after the response is returned. (See `https://isolgpus.github.io/Luxis/testing/` for the test client.)

## Common mistakes (don't do these)

- ❌ Returning a succeeded `Future` after a partial failure ("I'll just log the error"). Breaks at-least-once.
- ❌ Calling `.get()` / `.join()` inside `publish`. Blocks the event loop.
- ❌ Using `OutboxEvent.key` as a partition / message key. It's the destination.
- ❌ Adding retry loops inside `publish`. Luxis retries via the next drain pass.
- ❌ Holding mutable state across batches that affects ordering or dedup. Each batch is independent.
- ❌ Throwing from `publish` instead of returning a failed `Future`. Drainer treats both as failure but the contract is the failed-future shape.

## Authoritative reference

- Messaging guide — https://isolgpus.github.io/Luxis/guides/messaging/

If this skill and the docs disagree, the docs win.
