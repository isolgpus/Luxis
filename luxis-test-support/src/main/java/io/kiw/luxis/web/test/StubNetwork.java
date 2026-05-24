package io.kiw.luxis.web.test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StubNetwork {

    private final Map<String, TestLuxis<?>> servers = new ConcurrentHashMap<>();

    public void register(final String host, final int port, final TestLuxis<?> luxis) {
        servers.put(key(host, port), luxis);
    }

    public TestLuxis<?> resolve(final String host, final int port) {
        final TestLuxis<?> luxis = servers.get(key(host, port));
        if (luxis == null) {
            throw new IllegalStateException("No Luxis instance registered for " + key(host, port)
                    + ". Registered: " + servers.keySet());
        }
        return luxis;
    }

    private static String key(final String host, final int port) {
        final String normalized = ("127.0.0.1".equals(host) || "0.0.0.0".equals(host)) ? "localhost" : host;
        return normalized + ":" + port;
    }
}
