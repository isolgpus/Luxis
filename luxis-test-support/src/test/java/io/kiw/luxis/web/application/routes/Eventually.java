package io.kiw.luxis.web.application.routes;

public final class Eventually {

    private static final long DEFAULT_TIMEOUT_MILLIS = 5000;
    private static final long POLL_INTERVAL_MILLIS = 10;

    private Eventually() {
    }

    public static void eventually(final String mode, final Runnable assertion) {
        if (!TestApplicationClientCreator.REAL_MODE.equals(mode)) {
            assertion.run();
            return;
        }

        final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MILLIS;
        AssertionError lastFailure = null;
        while (true) {
            try {
                assertion.run();
                return;
            } catch (final AssertionError e) {
                lastFailure = e;
            }

            if (System.currentTimeMillis() >= deadline) {
                throw lastFailure;
            }

            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for assertion to succeed", ie);
            }
        }
    }
}
