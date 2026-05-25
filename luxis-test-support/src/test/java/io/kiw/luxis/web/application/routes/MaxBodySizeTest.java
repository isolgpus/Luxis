package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
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

import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class MaxBodySizeTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public MaxBodySizeTest(TestMode mode) {
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
    public void shouldRejectRequestExceedingMaxBodySize() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setMaxBodySize(10).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo")
                        .body(json().put("intExample", 42).put("stringExample", "this body is way too long").toString()));

        Assert.assertEquals(413, response.statusCode);
    }

    @Test
    public void shouldAcceptRequestWithinMaxBodySize() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setMaxBodySize(1000).build()));
        TestClient client = testClientAndServer.client();

        String body = json().put("intExample", 42).put("stringExample", "hello").toString();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo").body(body));

        Assert.assertEquals(200, response.statusCode);
    }

    @Test
    public void shouldNotEnforceBodyLimitWhenNotConfigured() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        String body = json().put("intExample", 42).put("stringExample", "any size body is fine").toString();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo").body(body));

        Assert.assertEquals(200, response.statusCode);
    }
}
