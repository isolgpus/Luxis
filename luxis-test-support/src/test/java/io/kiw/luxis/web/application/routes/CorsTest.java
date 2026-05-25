package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.cors.CorsConfigBuilder;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestFilter;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.GetEchoHandler;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import io.kiw.luxis.web.test.handler.TestFilterHandler;
import io.kiw.luxis.web.test.handler.TestFilterRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class CorsTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private CorsConfig defaultCorsConfig;
    private TestClientAndServer testClientAndServer;

    public CorsTest(TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (mode == TestMode.REAL) {
            assumeRealModeEnabled();
        }
        defaultCorsConfig = new CorsConfigBuilder()
                .allowOrigin("http://allowed.example.com")
                .allowOrigin("http://another.example.com")
                .allowMethod("GET")
                .allowMethod("POST")
                .allowMethod("PUT")
                .allowHeader("Content-Type")
                .allowHeader("Authorization")
                .exposeHeader("X-Custom-Header")
                .allowCredentials(true)
                .maxAgeSeconds(3600)
                .build();
    }

    @Test
    public void shouldReturnCorsHeadersOnPreflightRequest() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://allowed.example.com")
                        .headerParam("Access-Control-Request-Method", "POST"));

        assertEquals(204, response.statusCode);
        assertEquals("http://allowed.example.com", response.getHeader("access-control-allow-origin"));
        assertEquals("GET,POST,PUT", response.getHeader("access-control-allow-methods"));
        assertEquals("Content-Type,Authorization", response.getHeader("access-control-allow-headers"));
        assertEquals("true", response.getHeader("access-control-allow-credentials"));
        assertEquals("3600", response.getHeader("access-control-max-age"));
    }

    @Test
    public void shouldReturnCorsHeadersOnPreflightForSecondAllowedOrigin() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://another.example.com")
                        .headerParam("Access-Control-Request-Method", "GET"));

        assertEquals(204, response.statusCode);
        assertEquals("http://another.example.com", response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldRejectPreflightFromDisallowedOrigin() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://evil.example.com")
                        .headerParam("Access-Control-Request-Method", "POST"));

        assertEquals(403, response.statusCode);
        assertNull(response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldRejectPreflightWithNoOrigin() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/echo"));

        assertEquals(405, response.statusCode);
        assertNull(response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldAddCorsHeadersToNormalGetResponse() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://allowed.example.com"));

        assertEquals(200, response.statusCode);
        assertEquals("http://allowed.example.com", response.getHeader("access-control-allow-origin"));
        assertEquals("true", response.getHeader("access-control-allow-credentials"));
        assertEquals("X-Custom-Header", response.getHeader("access-control-expose-headers"));
    }

    @Test
    public void shouldAddCorsHeadersToNormalPostResponse() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://allowed.example.com")
                        .body("{}"));

        assertEquals(200, response.statusCode);
        assertEquals("http://allowed.example.com", response.getHeader("access-control-allow-origin"));
        assertEquals("true", response.getHeader("access-control-allow-credentials"));
    }

    @Test
    public void shouldNotAddCorsHeadersForDisallowedOriginOnNormalRequest() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://evil.example.com"));

        assertEquals(403, response.statusCode);
        assertNull(response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldNotAddCorsHeadersWhenNoOriginOnNormalRequest() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/echo"));

        assertEquals(200, response.statusCode);
        assertNull(response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldHandleWildcardOrigin() {
        CorsConfig wildcardConfig = new CorsConfigBuilder()
                .allowOrigin("*")
                .allowMethod("GET")
                .build();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(wildcardConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse preflight = client.options(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://any-origin.com")
                        .headerParam("Access-Control-Request-Method", "GET"));

        assertEquals(204, preflight.statusCode);
        assertEquals("*", preflight.getHeader("access-control-allow-origin"));

        TestHttpResponse normal = client.get(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://any-origin.com"));

        assertEquals(200, normal.statusCode);
        assertEquals("*", normal.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldWorkWithoutCredentialsOrMaxAge() {
        CorsConfig simpleConfig = new CorsConfigBuilder()
                .allowOrigin("http://simple.example.com")
                .allowMethod("GET")
                .build();
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(simpleConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/echo")
                        .headerParam("Origin", "http://simple.example.com")
                        .headerParam("Access-Control-Request-Method", "GET"));

        assertEquals(204, response.statusCode);
        assertEquals("http://simple.example.com", response.getHeader("access-control-allow-origin"));
        assertNull(response.getHeader("access-control-allow-credentials"));
    }

    @Test
    public void shouldNotInterfereWithNormalRequestsWhenNoCorsConfigured() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(StubRequest.request("/echo"));

        assertEquals(200, response.statusCode);
        assertNull(response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldAddCorsHeadersToFilteredRoutes() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.POST, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.post(
                StubRequest.request("/root/filter/test")
                        .headerParam("Origin", "http://allowed.example.com")
                        .body("{}"));

        assertEquals(200, response.statusCode);
        assertEquals("http://allowed.example.com", response.getHeader("access-control-allow-origin"));
    }

    @Test
    public void shouldHandlePreflightOnFilteredRoutes() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.POST, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setCorsConfig(defaultCorsConfig).build()));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.options(
                StubRequest.request("/root/filter/test")
                        .headerParam("Origin", "http://allowed.example.com")
                        .headerParam("Access-Control-Request-Method", "POST"));

        assertEquals(204, response.statusCode);
        assertEquals("http://allowed.example.com", response.getHeader("access-control-allow-origin"));
    }


    @After
    public void tearDown() throws Exception {
        if (testClientAndServer != null) {
            testClientAndServer.client().assertNoMoreExceptions();
            testClientAndServer.close();
        }
    }

}
