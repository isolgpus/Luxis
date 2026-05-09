package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.TransactionalWebSocketRoutes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createContextAsserter;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createTestServerAndClient;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class WebSocketTransactionTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestWebSocketClient ws;
    private TestClientAndServer testClientAndServer;

    public WebSocketTransactionTest(final String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
    }

    @After
    public void tearDown() {
        if (ws != null && !ws.isClosed()) {
            ws.close();
        }
        if (testClientAndServer != null) {
            try {
                testClientAndServer.luxis().close();
            } catch (final Exception ignored) {
            }
        }
    }

    @Test
    public void shouldRunTransactionalSubChainAndFireCommitLifecycle() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/tx", state, new TransactionalWebSocketRoutes(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/tx"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "echoResponse")
                            .set("payload", json().put("echo", "hello-queried-updated"))
                            .toString(),
                    received.get(0));

            client.assertNoMoreExceptions();
        });

        final List<String> events = tm.events();
        Assert.assertEquals(Arrays.asList("begin:1", "commit:1", "onCommitted:1"), events);
    }

    @Test
    public void shouldRollbackWhenFlatMapReturnsResultError() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/tx", state, new RollbackOnErrorWebSocketRoutes(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/tx"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "error")
                            .set("payload", json().put("message", "sub-chain failed").set("errors", json()))
                            .toString(),
                    received.get(0));
        });

        final List<String> events = tm.events();
        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), events);
    }

    @Test
    public void shouldRollbackWhenAsyncMapReturnsFailedFuture() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.webSocketRoute("/ws/tx", state, new AsyncMapThrowsTransactionalWebSocketRoutes());
        }, tm);
        final TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/tx"));
        ws.send("{\"type\":\"echo\",\"payload\":{\"message\":\"hello\"}}");

        ws.onResponses(received -> {
            Assert.assertEquals(0, received.size());
            Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
            client.assertException("async driver failed");
            client.assertNoMoreExceptions();
        });
    }
}
