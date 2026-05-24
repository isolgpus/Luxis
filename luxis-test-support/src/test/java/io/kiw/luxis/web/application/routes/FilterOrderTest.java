package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.GetTestFilterHandler;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import io.kiw.luxis.web.test.handler.TestFilterHandler;
import io.kiw.luxis.web.test.handler.TestFilterRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class FilterOrderTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public FilterOrderTest(String mode) {
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
    public void shouldExecuteFiltersInRegistrationOrder() {
        final List<String> executionOrder = new ArrayList<>();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonFilter("/ordered/*", state, e -> e.complete(ctx -> {
                executionOrder.add("first");
                ctx.session().addResponseCookie(new HttpCookie("filter-first", "hit"));
                return HttpResult.success();
            }));
            r.jsonFilter("/ordered/*", state, e -> e.complete(ctx -> {
                executionOrder.add("second");
                ctx.session().addResponseCookie(new HttpCookie("filter-second", "hit"));
                return HttpResult.success();
            }));
            r.jsonRoute("/ordered/test", Method.POST, state, TestFilterRequest.class, new TestFilterHandler());
        });
        TestClient client = testClientAndServer.client();

        client.post(StubRequest.request("/ordered/test").body(json().toString()));

        Assert.assertEquals(Arrays.asList("first", "second"), executionOrder);
    }

    @Test
    public void shouldExecuteNarrowAndBroadFiltersInRegistrationOrder() {
        final List<String> executionOrder = new ArrayList<>();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonFilter("/a/*", state, e -> e.complete(ctx -> {
                executionOrder.add("broad");
                ctx.session().addResponseCookie(new HttpCookie("broad", "hit"));
                return HttpResult.success();
            }));
            r.jsonFilter("/a/b/*", state, e -> e.complete(ctx -> {
                executionOrder.add("narrow");
                ctx.session().addResponseCookie(new HttpCookie("narrow", "hit"));
                return HttpResult.success();
            }));
            r.jsonRoute("/a/b/test", Method.GET, state, Void.class, new GetTestFilterHandler());
        });
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(StubRequest.request("/a/b/test"));

        Assert.assertEquals(Arrays.asList("broad", "narrow"), executionOrder);
        Assert.assertEquals("hit", response.getCookie("broad"));
        Assert.assertEquals("hit", response.getCookie("narrow"));
    }

    @Test
    public void shouldOnlyExecuteMatchingFilters() {
        final List<String> executionOrder = new ArrayList<>();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonFilter("/api/*", state, e -> e.complete(ctx -> {
                executionOrder.add("api");
                return HttpResult.success();
            }));
            r.jsonFilter("/admin/*", state, e -> e.complete(ctx -> {
                executionOrder.add("admin");
                return HttpResult.success();
            }));
            r.jsonRoute("/api/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        });
        TestClient client = testClientAndServer.client();

        client.post(StubRequest.request("/api/echo")
                .body(json().put("intExample", 1).put("stringExample", "test").toString()));

        Assert.assertEquals(Arrays.asList("api"), executionOrder);
    }
}
