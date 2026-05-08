package io.kiw.luxis.web.messaging;

import java.nio.ByteBuffer;

public record OutboxEvent(String key, Payload payload) {

    public sealed interface Payload {
        record Str(String value) implements Payload {
        }

        record Bytes(byte[] value) implements Payload {
        }

        record Buf(ByteBuffer value) implements Payload {
        }
    }

    public static OutboxEvent of(final String key, final String message) {
        return new OutboxEvent(key, new Payload.Str(message));
    }

    public static OutboxEvent of(final String key, final byte[] message) {
        return new OutboxEvent(key, new Payload.Bytes(message));
    }

    public static OutboxEvent of(final String key, final ByteBuffer message) {
        return new OutboxEvent(key, new Payload.Buf(message));
    }
}
