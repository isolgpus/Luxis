package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.StubNetwork;
import io.kiw.luxis.web.test.TestClient;

import java.util.Objects;

public final class TestClientAndServer implements AutoCloseable {
    private final TestClient client;
    private final Luxis<?> luxis;
    private final StubNetwork network;

    public TestClientAndServer(TestClient client, Luxis<?> luxis, StubNetwork network) {
        this.client = client;
        this.luxis = luxis;
        this.network = network;
    }

    @Override
    public void close() throws Exception {
        client.close();
        luxis.close();
    }

    public TestClient client() {
        return client;
    }

    public Luxis<?> luxis() {
        return luxis;
    }

    StubNetwork network() {
        return network;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TestClientAndServer) obj;
        return Objects.equals(this.client, that.client) &&
                Objects.equals(this.luxis, that.luxis) &&
                Objects.equals(this.network, that.network);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, luxis, network);
    }

    @Override
    public String toString() {
        return "TestClientAndServer[" +
                "client=" + client + ", " +
                "luxis=" + luxis + ", " +
                "network=" + network + ']';
    }

}
