package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.TestClient;

public record TestClientAndServer(TestClient client, Luxis<?> luxis) implements AutoCloseable {

    @Override
    public void close() throws Exception {
        client.close();
        luxis.close();
    }
}
