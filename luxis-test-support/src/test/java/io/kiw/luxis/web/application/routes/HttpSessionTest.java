package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.ContextAssertingAsyncBlockingHttpHandler;
import io.kiw.luxis.web.test.handler.ContextAssertingHttpHandler;
import io.kiw.luxis.web.test.handler.ContextAssertingPeekHttpHandler;
import io.kiw.luxis.web.test.handler.ContextRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class HttpSessionTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public HttpSessionTest(TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() throws Exception {
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
    public void shouldRunAllPipelineStagesOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/context", Method.POST, state, ContextRequest.class, new ContextAssertingHttpHandler(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        final String requestBody = json()
                .put("message", "hello")
                .toString();

        TestHttpResponse response = client.post(StubRequest.request("/context").body(requestBody));

        final String expectedResponse = json()
                .put("result", "hello flatMap blockingFlatMap map blocking2")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }

    @Test
    public void shouldRunAsyncBlockingMapOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/context-async-blocking", Method.POST, state, ContextRequest.class, new ContextAssertingAsyncBlockingHttpHandler(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        final String requestBody = json()
                .put("message", "hello")
                .toString();

        TestHttpResponse response = client.post(StubRequest.request("/context-async-blocking").body(requestBody));

        final String expectedResponse = json()
                .put("result", "hello blocking map")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }

    @Test
    public void shouldRunPeekAndBlockingPeekOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/context-peek", Method.POST, state, ContextRequest.class, new ContextAssertingPeekHttpHandler(asserter));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        final String requestBody = json()
                .put("message", "hello")
                .toString();

        TestHttpResponse response = client.post(StubRequest.request("/context-peek").body(requestBody));

        final String expectedResponse = json()
                .put("result", "hello afterPeek")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }
}
