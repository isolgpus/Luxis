package io.kiw.luxis.web.test;

import io.kiw.luxis.web.internal.JacksonUtil;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.BooleanSupplier;

public final class TestHelper {
    private TestHelper() {
    }

    public static final ObjectMapper MAPPER = JacksonUtil.createMapper();

    public static ObjectNode json() {
        return MAPPER.createObjectNode();
    }

    public static String file(final String fileContents) {
        return fileContents;
    }

    public static void awaitTrue(final String mode, final BooleanSupplier condition, final String message) {
        if ("stub".equals(mode)) {
            if (!condition.getAsBoolean()) {
                throw new AssertionError(message);
            }
            return;
        }
        final long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError(message);
            }
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(message, e);
            }
        }
    }
}
