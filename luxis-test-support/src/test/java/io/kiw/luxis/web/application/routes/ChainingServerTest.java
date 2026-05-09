package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.ChainForwardGetHandler;
import io.kiw.luxis.web.test.handler.HttpClientCallHandler;
import io.kiw.luxis.web.test.handler.HttpClientGetRequest;
import io.kiw.luxis.web.test.handler.SimpleGetHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createHttpClient;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createTestServerAndClient;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class ChainingServerTest {

    private static final String HOST = "127.0.0.1";
    private static final int INITIAL_CHAIN_PORT = 8090;
    private static final int CHAIN_SIZE = 5;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private List<TestClientAndServer> serverChain = List.of();

    public ChainingServerTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void assumeMode() {
        if (REAL_MODE.equals(mode)) {
//            assumeRealModeEnabled();
        }
    }

    @After
    public void tearDown() throws Exception {
        for (TestClientAndServer server : serverChain) {
            server.close();
        }
    }

    @Test
    public void shouldChainGetRequestThroughMultipleServers() {
        serverChain = new ArrayList<>();
        TestClientAndServer[] servers = new TestClientAndServer[CHAIN_SIZE];

        // Last server: returns value directly
        servers[CHAIN_SIZE - 1] = createTestServerAndClient(mode, (r, state) ->
                        r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(42)),
                builder -> builder.setPort(INITIAL_CHAIN_PORT + CHAIN_SIZE - 1));

        // Intermediate servers: forward GET to next server
        for (int i = CHAIN_SIZE - 2; i >= 1; i--) {
            final int port = INITIAL_CHAIN_PORT + i;
            final LuxisHttpClient httpClient = createHttpClient(mode, servers[i + 1]);
            final String nextUrl = "http://" + HOST + ":" + (port + 1) + "/api/value";
            servers[i] = createTestServerAndClient(mode, (r, state) ->
                            r.jsonRoute("/api/value", Method.GET, state, Void.class, new ChainForwardGetHandler(httpClient, nextUrl)),
                    builder -> builder.setPort(port));
        }

        // First server: accepts POST, forwards GET to second server
        final LuxisHttpClient httpClient = createHttpClient(mode, servers[1]);
        final String secondBaseUrl = "http://" + HOST + ":" + (INITIAL_CHAIN_PORT + 1);
        servers[0] = createTestServerAndClient(mode, (r, state) ->
                        r.jsonRoute("/call-next", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, secondBaseUrl)),
                builder -> builder.setPort(INITIAL_CHAIN_PORT));

        serverChain = List.of(servers);

        final TestHttpResponse response = servers[0].client().post(
                StubRequest.request("/call-next")
                        .body(json().put("targetPath", "/api/value").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("statusCode", 200)
                        .put("body", json().put("result", 42).toString())
                        .toString()),
                response);
    }


}
