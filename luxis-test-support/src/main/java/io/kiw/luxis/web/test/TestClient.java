package io.kiw.luxis.web.test;

public interface TestClient {
    TestHttpResponse post(StubRequest stubRequest);

    TestHttpResponse put(StubRequest stubRequest);

    TestHttpResponse delete(StubRequest stubRequest);

    TestHttpResponse patch(StubRequest stubRequest);

    TestHttpResponse get(StubRequest stubRequest);

    TestHttpResponse options(StubRequest stubRequest);

    TestWebSocketClient webSocket(StubRequest stubRequest);

    void assertException(String expected);

    void assertNoMoreExceptions();

    void close() throws Exception;
}
