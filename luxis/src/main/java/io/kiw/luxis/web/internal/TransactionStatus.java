package io.kiw.luxis.web.internal;

public final class TransactionStatus {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TransactionStatus() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        final int d = DEPTH.get() - 1;
        if (d <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(d);
        }
    }

    public static boolean isInTransaction() {
        return DEPTH.get() > 0;
    }
}
