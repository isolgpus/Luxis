package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createTestServerAndClient;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class MaxBodySizeTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestClientAndServer testClientAndServer;

    public MaxBodySizeTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
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
    public void shouldRejectRequestExceedingMaxBodySize() {
        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        }, builder -> builder.setMaxBodySize(10));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo")
                        .body(json().put("intExample", 42).put("stringExample", "this body is way too long").toString()));

        Assert.assertEquals(413, response.statusCode);
    }

    @Test
    public void shouldAcceptRequestWithinMaxBodySize() {
        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        }, builder -> builder.setMaxBodySize(1000));
        TestClient client = testClientAndServer.client();

        String body = json().put("intExample", 42).put("stringExample", "hello").toString();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo").body(body));

        Assert.assertEquals(200, response.statusCode);
    }

    @Test
    public void shouldNotEnforceBodyLimitWhenNotConfigured() {
        testClientAndServer = TestApplicationClientCreator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        });
        TestClient client = testClientAndServer.client();

        String body = json().put("intExample", 42).put("stringExample", "any size body is fine").toString();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo").body(body));

        Assert.assertEquals(200, response.statusCode);
    }
}
