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
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class ChainingServerTest {

    private static final String HOST = "127.0.0.1";
    private static final int INITIAL_CHAIN_PORT = 8090;
    private static final int CHAIN_SIZE = 100;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private List<TestClientAndServer> serverChain = List.of();

    public ChainingServerTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void assumeMode() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
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
        final List<TestClientAndServer> servers = new ArrayList<>();

        for (int i = 0; i < CHAIN_SIZE; i++) {
            final int port = INITIAL_CHAIN_PORT + i;
            final LuxisHttpClient httpClient = creator.createHttpClient(mode);

            if (i == CHAIN_SIZE - 1) {
                servers.add(creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(42));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(port).build())));
            } else if (i == 0) {
                final String secondBaseUrl = "http://" + HOST + ":" + (port + 1);
                servers.add(creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-next", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, secondBaseUrl));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(port).build())));
            } else {
                final String nextUrl = "http://" + HOST + ":" + (port + 1) + "/api/value";
                servers.add(creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new ChainForwardGetHandler(httpClient, nextUrl));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(port).build())));
            }
        }

        serverChain = servers;

        final TestHttpResponse response = servers.get(0).client().post(
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
