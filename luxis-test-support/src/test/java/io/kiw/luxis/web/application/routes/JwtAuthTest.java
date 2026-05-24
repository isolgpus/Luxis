package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.JwtFilter;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationRoutes;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.JwtFilterProtectedHandler;
import io.kiw.luxis.web.test.handler.JwtProtectedHandler;
import io.kiw.luxis.web.test.jwt.StubJwtProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.Map;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import static org.junit.Assert.assertEquals;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class JwtAuthTest {

    private static final String JWT_SECRET = TestApplicationRoutes.JWT_SECRET;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;
    private StubJwtProvider jwtProvider;

    public JwtAuthTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
        jwtProvider = new StubJwtProvider(JWT_SECRET);
    }

    @After
    public void tearDown() throws Exception {
        if (testClientAndServer != null) {
            testClientAndServer.client().assertNoMoreExceptions();
            testClientAndServer.close();
        }
    }

    @Test
    public void shouldAllowRequestWithValidJwt() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user123"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json().put("subject", "user123").toString()),
                response);
    }

    @Test
    public void shouldExposeAllClaimsFromToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user456", "role", "admin"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json().put("subject", "user456").toString()),
                response);
    }

    @Test
    public void shouldRejectRequestWithNoAuthorizationHeader() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected"));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Missing or invalid Authorization header")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void shouldRejectRequestWithMalformedBearerToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Bearer header.payload.badsignature"));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Invalid token signature")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void shouldRejectRequestWithTokenSignedByDifferentSecret() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();
        StubJwtProvider otherProvider = new StubJwtProvider("a-completely-different-secret");
        String token = otherProvider.generateToken(Map.of("sub", "attacker"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Invalid token signature")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void shouldRejectExpiredToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();
        long oneHourAgo = System.currentTimeMillis() / 1000 - 3600;
        String token = jwtProvider.generateToken(Map.of("sub", "user789", "exp", oneHourAgo));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Token has expired")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void shouldRejectTokenWithWrongHeaderFormat() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(new StubJwtProvider(JWT_SECRET)));

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user123"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/protected")
                        .headerParam("Authorization", "Basic " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Missing or invalid Authorization header")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void filterShouldAllowRequestWithValidJwt() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user123"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json().put("subject", "user123").toString()),
                response);
    }

    @Test
    public void filterShouldExposeAllClaimsFromToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user456", "role", "admin"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json().put("subject", "user456").toString()),
                response);
    }

    @Test
    public void filterShouldRejectRequestWithNoAuthorizationHeader() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test"));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Missing or invalid Authorization header")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void filterShouldRejectRequestWithMalformedBearerToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Bearer header.payload.badsignature"));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Invalid token signature")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void filterShouldRejectRequestWithTokenSignedByDifferentSecret() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();
        StubJwtProvider otherProvider = new StubJwtProvider("a-completely-different-secret");
        String token = otherProvider.generateToken(Map.of("sub", "attacker"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Invalid token signature")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void filterShouldRejectExpiredToken() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();
        long oneHourAgo = System.currentTimeMillis() / 1000 - 3600;
        String token = jwtProvider.generateToken(Map.of("sub", "user789", "exp", oneHourAgo));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Bearer " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Token has expired")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }

    @Test
    public void filterShouldRejectTokenWithWrongHeaderFormat() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/jwt/filter/*", state, new JwtFilter(new StubJwtProvider(JWT_SECRET)));
            r.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

            return state;
        }));
        TestClient client = testClientAndServer.client();
        String token = jwtProvider.generateToken(Map.of("sub", "user123"));

        TestHttpResponse response = client.get(
                StubRequest.request("/jwt/filter/test")
                        .headerParam("Authorization", "Basic " + token));

        assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Missing or invalid Authorization header")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response);
    }
}
