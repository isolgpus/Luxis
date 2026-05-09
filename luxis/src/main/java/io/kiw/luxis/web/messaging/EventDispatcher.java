package io.kiw.luxis.web.messaging;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface EventDispatcher {
    void dispatch(ByteBuffer message);
}
