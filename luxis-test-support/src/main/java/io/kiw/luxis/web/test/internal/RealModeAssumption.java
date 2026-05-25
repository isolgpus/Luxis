package io.kiw.luxis.web.test.internal;

import org.junit.Assume;

public final class RealModeAssumption {

    private RealModeAssumption() {
    }

    public static void assumeRealModeEnabled() {
        Assume.assumeTrue(
                "Skipping real server test: set TEST_MODE=VERTX to enable",
                "VERTX".equals(System.getenv("TEST_MODE")));
    }
}
