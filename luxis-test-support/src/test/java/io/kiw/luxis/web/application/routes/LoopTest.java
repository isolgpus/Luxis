package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.TestMode;
import io.kiw.luxis.web.test.handler.LoopAgentRequest;
import io.kiw.luxis.web.test.handler.LoopAgentTestHandler;
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
public class LoopTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public LoopTest(final TestMode mode) {
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
        if (testClientAndServer != null) {
            testClientAndServer.client().assertNoMoreExceptions();
            testClientAndServer.close();
        }
    }

    @Test
    public void shouldRunMultipleTurnsThenExitWhenConditionMet() {
        final AtomicLong iterationCounter = new AtomicLong();

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/agent", Method.POST, state, LoopAgentRequest.class, new LoopAgentTestHandler(iterationCounter));
            return state;
        }));

        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/agent").body(json().put("target", 3).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 3).put("turns", 3).toString()),
                response);
        Assert.assertEquals(3, iterationCounter.get());
    }

    @Test
    public void shouldExitAfterASingleTurn() {
        final AtomicLong iterationCounter = new AtomicLong();

        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/agent", Method.POST, state, LoopAgentRequest.class, new LoopAgentTestHandler(iterationCounter));
            return state;
        }));

        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/agent").body(json().put("target", 1).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("result", 1).put("turns", 1).toString()),
                response);
        Assert.assertEquals(1, iterationCounter.get());
    }
}
