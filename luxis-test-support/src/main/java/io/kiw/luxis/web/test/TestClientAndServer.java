package io.kiw.luxis.web.test;

import io.kiw.luxis.web.Luxis;

public record TestClientAndServer(TestClient client, Luxis<?> luxis) implements AutoCloseable {

    @Override
    public void close() throws Exception {
        client.close();
        luxis.close();
    }
}
