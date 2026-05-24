package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.WebSocketRouteConfigBuilder;
import io.kiw.luxis.web.pipeline.SendErrorResponse;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.OnCloseTrackingSplitWebSocketRoutes;
import io.kiw.luxis.web.test.handler.SplitWebSocketRoutes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.awaitTrue;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class WebSocketSplitTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestWebSocketClient ws;
    private TestClientAndServer testClientAndServer;

    public WebSocketSplitTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
    }

    @Test
    public void shouldRouteDifferentMessageTypes() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: hello")).toString(),
                    received.get(0));
        });

        ws.send("{\"type\":\"number\",\"payload\":{\"value\":21}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 42)).toString(),
                    received.get(0));
        });
    }

    @Test
    public void shouldHandleMultipleMessagesOfDifferentTypes() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"first\"}}");
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":5}}");
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"second\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(3, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: first")).toString(),
                    received.get(0));
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 10)).toString(),
                    received.get(1));
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: second")).toString(),
                    received.get(2));
        });
    }

    @Test
    public void shouldDisconnectOnUnknownType() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"type\":\"unknown\",\"payload\":{}}");

        awaitTrue(mode, ws::isClosed, "Expected WebSocket to be closed after unknown type");
    }

    @Test
    public void shouldDisconnectOnInvalidJson() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("not valid json");

        awaitTrue(mode, ws::isClosed, "Expected WebSocket to be closed after invalid JSON");
    }

    @Test
    public void shouldDisconnectOnBadPayload() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"type\":\"number\",\"payload\":\"not an object\"}");

        awaitTrue(mode, ws::isClosed, "Expected WebSocket to be closed after bad payload");
    }

    @Test
    public void shouldSendErrorResponseOnUnknownTypeWhenConfigured() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .corruptInputStrategy(new SendErrorResponse("{\"error\":\"bad input\"}"))
                            .build());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"type\":\"unknown\",\"payload\":{}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals("{\"error\":\"bad input\"}", received.get(0));
        });

        Assert.assertFalse(ws.isClosed());
    }

    @Test
    public void shouldSendErrorResponseOnInvalidJsonWhenConfigured() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .corruptInputStrategy(new SendErrorResponse("{\"error\":\"bad json\"}"))
                            .build());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("not valid json");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals("{\"error\":\"bad json\"}", received.get(0));
        });

        Assert.assertFalse(ws.isClosed());
    }

    @Test
    public void shouldCallOnOpenAndOnClose() {
        final OnCloseTrackingSplitWebSocketRoutes handler = new OnCloseTrackingSplitWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, handler);
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));

        ws.close();

        awaitTrue(mode, ws::isClosed, "Expected WebSocket to be closed after missing type");

        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldDisconnectOnMissingType() {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/split", state, new SplitWebSocketRoutes());
        });
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/split"));
        ws.send("{\"payload\":{\"message\":\"hello\"}}");

        awaitTrue(mode, ws::isClosed, "Expected WebSocket to be closed after missing type");
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
}
