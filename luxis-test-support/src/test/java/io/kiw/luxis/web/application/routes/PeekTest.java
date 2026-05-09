package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.BlockingRequest;
import io.kiw.luxis.web.test.handler.PeekTestHandler;
import io.kiw.luxis.web.test.handler.PeekWebSocketRoutes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class PeekTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestClientAndServer testClientAndServer;
    private TestWebSocketClient ws;

    public PeekTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() throws Exception {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (ws != null) {
            ws.close();
        }
        if (testClientAndServer != null) {
            testClientAndServer.client().assertNoMoreExceptions();
            testClientAndServer.close();
        }
    }

    @Test
    public void shouldExecutePeekSideEffectAndPassThroughValue() {
        final PeekTestHandler handler = new PeekTestHandler();
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/peek", Method.POST, state, BlockingRequest.class, handler);
        });
        TestClient client = testClientAndServer.client();

        final String requestBody = json()
                .put("numberToMultiply", 7)
                .toString();

        TestHttpResponse response = client.post(StubRequest.request("/peek").body(requestBody));

        Assert.assertEquals(TestHttpResponse.response(json().put("multipliedNumber", 21).toString()), response);
        Assert.assertEquals(1, handler.peekCount.get());
        Assert.assertEquals(1, handler.blockingPeekCount.get());
    }

    @Test
    public void shouldExecutePeekSideEffectsOnMultipleRequests() {
        final PeekTestHandler handler = new PeekTestHandler();
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/peek", Method.POST, state, BlockingRequest.class, handler);
        });
        TestClient client = testClientAndServer.client();

        client.post(StubRequest.request("/peek").body(json().put("numberToMultiply", 1).toString()));
        client.post(StubRequest.request("/peek").body(json().put("numberToMultiply", 2).toString()));

        Assert.assertEquals(2, handler.peekCount.get());
        Assert.assertEquals(2, handler.blockingPeekCount.get());
    }

    @Test
    public void shouldExecuteWebSocketPeekSideEffectAndPassThroughValue() {
        final PeekWebSocketRoutes handler = new PeekWebSocketRoutes();
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/peek", state, handler);
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/peek"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":4}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 12)).toString(),
                    received.get(0));

            Assert.assertEquals(1, handler.peekCount.get());
            Assert.assertEquals(1, handler.blockingPeekCount.get());

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldExecuteWebSocketPeekSideEffectsOnMultipleMessages() {
        final PeekWebSocketRoutes handler = new PeekWebSocketRoutes();
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/peek", state, handler);
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/peek"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":1}}");
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":2}}");

        ws.onResponses(received -> {
            Assert.assertEquals(2, received.size());
            Assert.assertEquals(2, handler.peekCount.get());
            Assert.assertEquals(2, handler.blockingPeekCount.get());

            client.assertNoMoreExceptions();
        });
    }
}
