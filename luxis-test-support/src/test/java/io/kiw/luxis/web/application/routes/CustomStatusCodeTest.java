package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.StatusCodeRequest;
import io.kiw.luxis.web.test.handler.StatusCodeTestHandler;
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
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class CustomStatusCodeTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public CustomStatusCodeTest(String mode) {
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
    public void shouldAllowHandlerToSetCreatedStatusCode() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/statusCode", Method.POST, state, StatusCodeRequest.class, new StatusCodeTestHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/statusCode")
                        .body(json().put("statusCode", "CREATED").put("value", "created").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("value", "created").toString()).withStatusCode(201),
                response);
    }

    @Test
    public void shouldAllowHandlerToSetNoContentStatusCode() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/statusCode", Method.POST, state, StatusCodeRequest.class, new StatusCodeTestHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/statusCode")
                        .body(json().put("statusCode", "NO_CONTENT").put("value", "done").toString()));

        Assert.assertEquals(
                TestHttpResponse.response("").withStatusCode(204),
                response);
    }

    @Test
    public void shouldDefaultToOkWhenStatusCodeNotSet() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/statusCode", Method.POST, state, StatusCodeRequest.class, new StatusCodeTestHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/statusCode")
                        .body(json().put("statusCode", "OK").put("value", "ok").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("value", "ok").toString()),
                response);
    }
}
