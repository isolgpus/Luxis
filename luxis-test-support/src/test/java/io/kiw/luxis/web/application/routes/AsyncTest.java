package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestLuxis;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.AsyncCustomTimeoutTestHandler;
import io.kiw.luxis.web.test.handler.AsyncMapRequest;
import io.kiw.luxis.web.test.handler.AsyncRetryTestHandler;
import io.kiw.luxis.web.test.handler.AsyncRetryWebSocketRoutes;
import io.kiw.luxis.web.test.handler.AsyncThrowTestHandler;
import io.kiw.luxis.web.test.handler.TestRetryBehaviour;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class AsyncTest {


    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;
    private TestWebSocketClient ws;

    public AsyncTest(final TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (mode == TestMode.REAL) {
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
    public void shouldHandleExceptionInAsyncMapHandler() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/throw", Method.POST, state, AsyncMapRequest.class, new AsyncThrowTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/throw").body(json().put("value", 1).toString()));

        Assert.assertEquals(500, response.statusCode);
        luxisTestClient.assertException("app error in asyncMap");
    }

    @Test
    public void shouldTimeoutWithCustomOneSecondTimeout() {
        final AsyncCustomTimeoutTestHandler handler = new AsyncCustomTimeoutTestHandler();

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/customTimeout", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        if (mode == TestMode.STUB) {
            handler.setOnRegistered(() -> ((TestLuxis<?>) testClientAndServer.luxis()).advanceTimeBy(201));
        }

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/customTimeout").body(json().put("value", 1).toString()));

        Assert.assertEquals(500, response.statusCode);
        luxisTestClient.assertException("Async response timed out");
    }

    @Test
    public void shouldRetryOnFailure() {

        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().error().error().error().error());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/customTimeout", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));


        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/customTimeout").body(json().put("value", 1).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Failed running async")
                        .set("errors", json()).toString()).withStatusCode(500),
                response);
        luxisTestClient.assertNoMoreExceptions();
    }

    @Test
    public void shouldSucceedOnFirstAttemptWithoutRetrying() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 7).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 7).toString()),
                response);
        Assert.assertEquals(1, counter.get());
    }

    @Test
    public void shouldSucceedAfterErrorRetries() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().error().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 5).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 5).toString()),
                response);
        Assert.assertEquals(3, counter.get());
    }

    @Test
    public void shouldSucceedOnLastRetryAttempt() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().error().error().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 3).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 3).toString()),
                response);
        Assert.assertEquals(4, counter.get());
    }

    @Test
    public void shouldSucceedAfterExceptionAndCallExceptionHandler() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().exception().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 9).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 9).toString()),
                response);
        Assert.assertEquals(2, counter.get());
        luxisTestClient.assertNoMoreExceptions();
    }

    @Test
    public void shouldSucceedAfterMixOfErrorsAndExceptions() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().error().exception().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 4).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 4).toString()),
                response);
        Assert.assertEquals(4, counter.get());
        luxisTestClient.assertNoMoreExceptions();
    }

    @Test
    public void shouldFailAndReportAllExceptionsWhenAllAttemptsThrow() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour()
                .exception()
                .exception()
                .exception()
                .exception());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 1).toString()));

        Assert.assertEquals(500, response.statusCode);
        Assert.assertEquals(4, counter.get());
        luxisTestClient.assertException("Async exception on attempt 3");
    }

    @Test
    public void shouldFailWithExceptionsReportedWhenMixedFailuresExhaustRetries() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryTestHandler handler = new AsyncRetryTestHandler(counter, new TestRetryBehaviour().exception().error().exception().error());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/retry", Method.POST, state, AsyncMapRequest.class, handler);

            return state;
        }));

        final TestClient luxisTestClient = testClientAndServer.client();

        final TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/retry").body(json().put("value", 1).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Failed running async")
                        .set("errors", json()).toString()).withStatusCode(500),
                response);
        Assert.assertEquals(4, counter.get());
        luxisTestClient.assertNoMoreExceptions();
    }

    @Test
    public void shouldRetryWebSocketAsyncMapOnFailure() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().error().error().error().error());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":1}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "Failed running async").set("errors", json())).toString(),
                    received.get(0));

            Assert.assertEquals(4, counter.get());
        });
    }

    @Test
    public void shouldSucceedWebSocketAsyncMapOnFirstAttemptWithoutRetrying() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":7}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 7)).toString(),
                    received.get(0));

            Assert.assertEquals(1, counter.get());
        });
    }

    @Test
    public void shouldSucceedWebSocketAsyncMapAfterErrorRetries() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().error().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":5}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 5)).toString(),
                    received.get(0));

            Assert.assertEquals(3, counter.get());
        });
    }

    @Test
    public void shouldSucceedWebSocketAsyncMapOnLastRetryAttempt() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().error().error().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":3}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 3)).toString(),
                    received.get(0));

            Assert.assertEquals(4, counter.get());
        });
    }

    @Test
    public void shouldSucceedWebSocketAsyncMapAfterExceptionAndCallExceptionHandler() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().exception().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":9}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 9)).toString(),
                    received.get(0));

            Assert.assertEquals(2, counter.get());
        });
    }

    @Test
    public void shouldSucceedWebSocketAsyncMapAfterMixOfErrorsAndExceptions() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().error().exception().error().success());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":4}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 4)).toString(),
                    received.get(0));

            Assert.assertEquals(4, counter.get());
        });
    }

    @Test
    public void shouldFailWebSocketAsyncMapAndReportAllExceptionsWhenAllAttemptsThrow() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour()
                .exception()
                .exception()
                .exception()
                .exception());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":1}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "Something went wrong").set("errors", json())).toString(),
                    received.get(0));

            Assert.assertEquals(4, counter.get());
            client.assertException("Async exception on attempt 3");
        });
    }

    @Test
    public void shouldFailWebSocketAsyncMapWithMixedFailuresExhaustingRetries() {
        final AtomicLong counter = new AtomicLong();
        final AsyncRetryWebSocketRoutes handler = new AsyncRetryWebSocketRoutes(counter, new TestRetryBehaviour().exception().error().exception().error());

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/retry", state, handler);

            return state;
        }));

        TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/retry"));
        ws.send("{\"type\":\"number\",\"payload\":{\"value\":1}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error").set("payload", json().put("message", "Failed running async").set("errors", json())).toString(),
                    received.get(0));

            Assert.assertEquals(4, counter.get());
        });
    }
}
