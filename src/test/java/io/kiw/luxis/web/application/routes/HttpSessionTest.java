package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.ContextAssertingAsyncBlockingHttpHandler;
import io.kiw.luxis.web.test.handler.ContextAssertingAsyncHttpHandler;
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

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class HttpSessionTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestClientAndServer testClientAndServer;

    public HttpSessionTest(String mode) {
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
        if (testClientAndServer != null) {
            testClientAndServer.client().assertNoMoreExceptions();
            testClientAndServer.close();
        }
    }

    @Test
    public void shouldRunAllPipelineStagesOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/context", Method.POST, state, ContextRequest.class, new ContextAssertingHttpHandler(asserter));
        });
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
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/context-async-blocking", Method.POST, state, ContextRequest.class, new ContextAssertingAsyncBlockingHttpHandler(asserter));
        });
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
    public void shouldRunCorrelatedAsyncOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        final ContextAssertingAsyncHttpHandler handler = new ContextAssertingAsyncHttpHandler(asserter);
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/context-correlated", Method.POST, state, ContextRequest.class, handler);
        });
        handler.evillyReferenceLuxis(testClientAndServer.luxis());
        TestClient client = testClientAndServer.client();

        final String requestBody = json()
                .put("message", "hello")
                .toString();

        TestHttpResponse response = client.post(StubRequest.request("/context-correlated").body(requestBody));

        final String expectedResponse = json()
                .put("result", "hello async blocking asyncBlocking map")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }

    @Test
    public void shouldRunPeekAndBlockingPeekOnCorrectContext() {
        final ContextAsserter asserter = TestApplicationClientCreator.createContextAsserter(mode);
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/context-peek", Method.POST, state, ContextRequest.class, new ContextAssertingPeekHttpHandler(asserter));
        });
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
