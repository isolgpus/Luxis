package io.kiw.luxis.web.test;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.internal.StubRouter;

public class StubTestClient implements TestClient {

    private final StubNetwork network;
    private final String host;
    private final int port;

    public StubTestClient(final String host, final int port, final StubNetwork network) {
        this.host = host;
        this.port = port;
        this.network = network;
    }

    private TestLuxis<?> testLuxis() {
        return network.resolve(host, port);
    }

    private StubRouter router() {
        return testLuxis().getRouter();
    }

    @Override
    public TestHttpResponse post(final StubRequest stubRequest) {

        return router().handle(stubRequest, Method.POST);
    }

    @Override
    public TestHttpResponse put(final StubRequest stubRequest) {

        return router().handle(stubRequest, Method.PUT);
    }

    @Override
    public TestHttpResponse delete(final StubRequest stubRequest) {

        return router().handle(stubRequest, Method.DELETE);
    }

    @Override
    public TestHttpResponse patch(final StubRequest stubRequest) {

        return router().handle(stubRequest, Method.PATCH);
    }

    @Override
    public TestHttpResponse get(final StubRequest stubRequest) {
        return router().handle(stubRequest, Method.GET);
    }

    @Override
    public TestHttpResponse options(final StubRequest stubRequest) {
        return router().handle(stubRequest, Method.OPTIONS);
    }

    @Override
    public StubTestWebSocketClient webSocket(final StubRequest stubRequest) {
        return router().webSocket(stubRequest);
    }

    @Override
    public void assertNoMoreExceptions() {
        testLuxis().assertNoMoreExceptions();
    }

    @Override
    public void assertException(final String message) {
        testLuxis().assertException(message);
    }

    @Override
    public void close() throws Exception {

    }
}
