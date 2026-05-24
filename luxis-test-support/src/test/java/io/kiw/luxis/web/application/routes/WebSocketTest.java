package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.test.TestLuxis;
import io.kiw.luxis.web.WebSocketRouteConfigBuilder;
import io.kiw.luxis.web.pipeline.DisconnectSession;
import io.kiw.luxis.web.pipeline.JustSendValidationError;
import io.kiw.luxis.web.pipeline.SendErrorResponse;
import io.kiw.luxis.web.pipeline.SendValidationErrorsAndDisconnectSession;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHelper;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.AsyncBlockingMapWebSocketRoutes;
import io.kiw.luxis.web.test.handler.AsyncFlatMapFailWebSocketRoutes;
import io.kiw.luxis.web.test.handler.AsyncMapWebSocketRoutes;
import io.kiw.luxis.web.test.handler.BlockingFlatMapFailWebSocketRoutes;
import io.kiw.luxis.web.test.handler.BlockingMapWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ContextAssertingAsyncWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ContextAssertingBlockingCompleteWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ContextAssertingPeekWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ContextAssertingWebSocketRoutes;
import io.kiw.luxis.web.test.handler.EchoWebSocketRoutes;
import io.kiw.luxis.web.test.handler.FlatMapFailWebSocketRoutes;
import io.kiw.luxis.web.test.handler.NoResponseWebSocketRoutes;
import io.kiw.luxis.web.test.handler.OnCloseTrackingWebSocketRoutes;
import io.kiw.luxis.web.test.handler.StatefulWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ThrowWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ValidationWebSocketRoutes;
import io.kiw.luxis.web.test.handler.WebSocketCustomTimeoutRoutes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.kiw.luxis.web.application.routes.Eventually.eventually;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.STUB_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import static org.junit.Assert.fail;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class WebSocketTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestWebSocketClient ws;
    private TestClientAndServer testClientAndServer;

    public WebSocketTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
    }

    @Test
    public void shouldEchoWebSocketMessage() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/echo"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: hello")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleMultipleMessages() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/echo"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"first\"}}");
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"second\"}}");

        ws.onResponses((received -> {
            Assert.assertEquals(2, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: first")).toString(),
                    received.get(0));
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: second")).toString(),
                    received.get(1));

            client.assertNoMoreExceptions();
        }));
    }

    @Test
    public void shouldSendMessageOnConnect() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/chat/:room", state, new StatefulWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/chat/general"));

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(json().put("type", "echoResponse").set("payload", json().put("echo", "connected")).toString(), received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldCloseWebSocket() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/chat/:room", state, new StatefulWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/chat/general"));
        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(json().put("type", "echoResponse").set("payload", json().put("echo", "connected")).toString(), received.get(0));
        });
        Assert.assertFalse(ws.isClosed());
        ws.close();

        Assert.assertTrue(ws.isClosed());

        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldThrowWhenNoWebSocketRouteMatches() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        try {
            client.webSocket(StubRequest.request("/ws/nonexistent"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("No WebSocket route registered for path: /ws/nonexistent", e.getMessage());
        } catch (RuntimeException e) {
            Assert.assertEquals("WebSocket connection failed", e.getMessage());
        }
    }

    @Test
    public void shouldHandleInvalidJsonGracefully() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/echo"));
        ws.send("not valid json");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());
            Assert.assertTrue(ws.isClosed());
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldMapThroughBlockingCall() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/blocking", state, new BlockingMapWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/blocking"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":22}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 44)).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldMapThroughAsyncMap() {
        final AsyncMapWebSocketRoutes handler = new AsyncMapWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/asyncMap", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/asyncMap"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":5}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 50)).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldMapThroughAsyncBlockingMap() {
        final AsyncBlockingMapWebSocketRoutes handler = new AsyncBlockingMapWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/asyncBlockingMap", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/asyncBlockingMap"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":3}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 60)).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnErrorOnFlatMapFailure() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/flatMapFail", state, new FlatMapFailWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/flatMapFail"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "flatMap failed").set("errors", json())).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnErrorOnBlockingFlatMapFailure() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/blockingFlatMapFail", state, new BlockingFlatMapFailWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/blockingFlatMapFail"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "blocking flatMap failed").set("errors", json())).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnErrorOnAsyncFlatMapFailure() {
        final AsyncFlatMapFailWebSocketRoutes handler = new AsyncFlatMapFailWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/asyncFlatMapFail", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/asyncFlatMapFail"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "async flatMap failed").set("errors", json())).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleExceptionInMapHandler() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"map\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());

            client.assertException("app error in map");
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleExceptionInBlockingHandler() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"blocking\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());

            client.assertException("app error in blocking");
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleExceptionInAsyncMapHandler() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"asyncMap\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());

            client.assertException("app error in asyncMap");
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleExceptionInAsyncBlockingMapHandler() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"asyncBlockingMap\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());

            client.assertException("app error in asyncBlockingMap");
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldHandleExceptionInCompleteHandler() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"complete\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());

            client.assertException("app error in complete");
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldNotSendResponseWhenCompleteWithNoResponse() {
        final NoResponseWebSocketRoutes handler = new NoResponseWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/noresponse", state, handler);

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/noresponse"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());
            Assert.assertTrue(handler.messageReceived);

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldPassThroughAllStagesWhenNoException() {
        final ThrowWebSocketRoutes handler = new ThrowWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/throw", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/throw"));
        ws.send("{\"type\":\"throw\",\"payload\":{\"where\":\"none\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "ok")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldPassValidationAndReturnResponse() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "validationResponse").set("payload",
                                    json()
                                            .put("name", "Alice")
                                            .put("email", "alice@example.com")
                                            .put("age", 25)
                                            .put("city", "NYC"))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnValidationErrorForInvalidBodyField() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .putNull("name")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("name", TestHelper.MAPPER.createArrayNode().add("must not be blank"))))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnValidationErrorForInvalidEmail() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .put("name", "Alice")
                .put("email", "not-an-email")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("email", TestHelper.MAPPER.createArrayNode().add("must be a valid email address"))))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnValidationErrorForNestedField() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "bad"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("address.zip", TestHelper.MAPPER.createArrayNode().add("must match pattern: [0-9]{5}"))))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldReturnValidationErrorForMultipleFields() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .putNull("name")
                .put("email", "not-an-email")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("name", TestHelper.MAPPER.createArrayNode().add("must not be blank"))
                                                    .set("email", TestHelper.MAPPER.createArrayNode().add("must be a valid email address"))))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldJustSendValidationErrorByDefault() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .failedValidationStrategy(JustSendValidationError.INSTANCE)
                            .build());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .putNull("name")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("name", TestHelper.MAPPER.createArrayNode().add("must not be blank"))))
                            .toString(),
                    received.get(0));

            Assert.assertFalse(ws.isClosed());
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldDisconnectSessionOnValidationFailure() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .failedValidationStrategy(DisconnectSession.INSTANCE)
                            .build());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .putNull("name")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());
            Assert.assertTrue(ws.isClosed());
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldSendValidationErrorsAndDisconnectSession() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/validate", state, new ValidationWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .failedValidationStrategy(SendValidationErrorsAndDisconnectSession.INSTANCE)
                            .build());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/validate"));

        String payload = json()
                .putNull("name")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        ws.send("{\"type\":\"validate\",\"payload\":" + payload + "}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload",
                                    json()
                                            .put("message", "Validation failed")
                                            .set("errors", json()
                                                    .set("name", TestHelper.MAPPER.createArrayNode().add("must not be blank"))))
                            .toString(),
                    received.get(0));

            Assert.assertTrue(ws.isClosed());
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldRunBlockingCompleteOnWorkerContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/blocking-complete", state, new ContextAssertingBlockingCompleteWebSocketRoutes(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/blocking-complete"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "echo: hello")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldRunMapAndBlockingMapOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/context", state, new ContextAssertingWebSocketRoutes(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/context"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "hello blocked")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldRunPeekAndBlockingPeekOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/context-peek", state, new ContextAssertingPeekWebSocketRoutes(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/context-peek"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "hello afterPeek")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldRunAsyncMapOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        final ContextAssertingAsyncWebSocketRoutes handler = new ContextAssertingAsyncWebSocketRoutes(asserter);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/context-async", state, handler);

            return state;
        }));
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/context-async"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse").set("payload", json().put("echo", "hello asyncmap async2 async3 blocking")).toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldTriggerOnCloseWhenClientDisconnects() {
        final OnCloseTrackingWebSocketRoutes handler = new OnCloseTrackingWebSocketRoutes();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/lifecycle", state, handler);

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/lifecycle"));
        eventually(mode, () -> Assert.assertTrue(handler.onOpenCalled));
        Assert.assertFalse(handler.onCloseCalled);

        ws.close();

        eventually(mode, () -> Assert.assertTrue(handler.onCloseCalled));

        ws = null;
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldSendErrorResponseOnCorruptInputWhenConfigured() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes(),
                    new WebSocketRouteConfigBuilder()
                            .corruptInputStrategy(new SendErrorResponse("{\"error\":\"bad json\"}"))
                            .build());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/echo"));
        ws.send("not valid json");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals("{\"error\":\"bad json\"}", received.get(0));
            Assert.assertFalse(ws.isClosed());

            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldDisconnectOnCorruptInputByDefault() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/echo"));
        ws.send("not valid json");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());
            Assert.assertTrue(ws.isClosed());
            client.assertNoMoreExceptions();
        });
    }

    @Test
    public void shouldTimeoutWebSocketWithCustomOneSecondTimeout() {
        final WebSocketCustomTimeoutRoutes handler = new WebSocketCustomTimeoutRoutes();

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/customTimeout", state, handler);

            return state;
        }));

        if (STUB_MODE.equals(mode)) {
            handler.setOnRegistered(() -> ((TestLuxis<?>) testClientAndServer.luxis()).advanceTimeBy(1_001));
        }

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/customTimeout"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":1}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "Something went wrong").set("errors", json())).toString(),
                    received.get(0));

            client.assertException("Correlated async response timed out");
        });
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
