package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestMode;
import io.kiw.luxis.web.test.TestWebSocketClient;
import io.kiw.luxis.web.test.handler.LoopWebSocketRoutes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import static io.kiw.luxis.web.test.TestHelper.json;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;

@RunWith(Parameterized.class)
public class WebSocketLoopTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestWebSocketClient ws;
    private TestClientAndServer testClientAndServer;

    public WebSocketLoopTest(final TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (mode == TestMode.REAL) {
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
    public void shouldRunMultipleTurnsThenExitOverWebSocket() {
        final AtomicLong iterationCounter = new AtomicLong();

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/loop", state, new LoopWebSocketRoutes(iterationCounter));

            return state;
        }));
        final TestClient client = testClientAndServer.client();

        ws = client.webSocket(StubRequest.request("/ws/loop"));
        ws.send("{\"type\":\"loop\",\"payload\":{\"value\":3}}");

        ws.onResponses(received -> {
            Assert.assertEquals(1, received.size());
            Assert.assertEquals(
                    json().put("type", "numberResponse").set("payload", json().put("result", 3)).toString(),
                    received.get(0));
            Assert.assertEquals(3, iterationCounter.get());

            client.assertNoMoreExceptions();
        });
    }
}
