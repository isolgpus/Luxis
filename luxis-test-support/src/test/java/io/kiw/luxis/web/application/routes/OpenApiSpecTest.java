package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.RouteConfigBuilder;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.JacksonUtil;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.GetEchoHandler;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class OpenApiSpecTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;
    private final ObjectMapper objectMapper = JacksonUtil.createMapper();

    public OpenApiSpecTest(String mode) {
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

    private JsonNode getSpec(TestClient luxisTestClient) throws Exception {
        TestHttpResponse response = luxisTestClient.get(StubRequest.request("/openapi.json"));
        assertEquals(200, response.statusCode);
        return objectMapper.readTree(response.responseBody);
    }

    @Test
    public void shouldGenerateSpecWithCorrectVersion() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);

        assertEquals("3.0.3", spec.get("openapi").asText());
        assertEquals("Test API", spec.get("info").get("title").asText());
        assertEquals("1.0.0", spec.get("info").get("version").asText());
        assertEquals("A test API", spec.get("info").get("description").asText());
    }

    @Test
    public void shouldGeneratePathsForRegisteredRoutes() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode paths = spec.get("paths");

        assertNotNull(paths.get("/echo"));
        assertNotNull(paths.get("/echo").get("post"));
        assertNotNull(paths.get("/echo").get("get"));
    }

    @Test
    public void shouldConvertPathParamsToOpenApiFormat() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo/:pathExample", Method.GET, state, Void.class, new GetEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .paramDescription("pathExample", "An example path param")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode paths = spec.get("paths");

        assertNotNull(paths.get("/openapi/echo/{pathExample}"));
        assertNull(paths.get("/openapi/echo/:pathExample"));

        JsonNode parameters = paths.get("/openapi/echo/{pathExample}").get("get").get("parameters");
        assertNotNull(parameters);
        boolean hasPathExample = false;
        for (JsonNode param : parameters) {
            if ("pathExample".equals(param.get("name").asText()) && "path".equals(param.get("in").asText())) {
                assertTrue(param.get("required").asBoolean());
                hasPathExample = true;
            }
        }
        assertTrue("Should have pathExample path parameter", hasPathExample);
    }

    @Test
    public void shouldGenerateRequestBodySchemaFromInputType() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .summary("Echo the input")
                            .description("Echoes back the provided values")
                            .tag("echo")
                            .responseDescription("The echoed response")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode requestBody = spec.get("paths").get("/openapi/echo").get("post").get("requestBody");

        assertNotNull(requestBody);
        assertTrue(requestBody.get("required").asBoolean());

        JsonNode schema = requestBody.get("content").get("application/json").get("schema");
        assertEquals("object", schema.get("type").asText());

        JsonNode properties = schema.get("properties");
        assertNotNull(properties.get("intExample"));
        assertEquals("integer", properties.get("intExample").get("type").asText());
        assertNotNull(properties.get("stringExample"));
        assertEquals("string", properties.get("stringExample").get("type").asText());
    }

    @Test
    public void shouldGenerateResponseSchemaFromOutputType() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .summary("Echo the input")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode response = spec.get("paths").get("/openapi/echo").get("post").get("responses").get("200");

        assertNotNull(response);

        JsonNode schema = response.get("content").get("application/json").get("schema");
        assertEquals("object", schema.get("type").asText());

        JsonNode properties = schema.get("properties");
        assertNotNull(properties.get("intExample"));
        assertNotNull(properties.get("stringExample"));
        assertNotNull(properties.get("pathExample"));
        assertNotNull(properties.get("queryExample"));
    }

    @Test
    public void shouldNotIncludeRequestBodyForGetRoutes() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode getOperation = spec.get("paths").get("/echo").get("get");

        assertNull(getOperation.get("requestBody"));
    }

    @Test
    public void shouldApplyOpenApiMetadata() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .summary("Echo the input")
                            .description("Echoes back the provided values")
                            .tag("echo")
                            .responseDescription("The echoed response")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode operation = spec.get("paths").get("/openapi/echo").get("post");

        assertEquals("Echo the input", operation.get("summary").asText());
        assertEquals("Echoes back the provided values", operation.get("description").asText());
        assertEquals("echo", operation.get("tags").get(0).asText());
        assertEquals("The echoed response", operation.get("responses").get("200").get("description").asText());
    }

    @Test
    public void shouldApplyParamDescriptions() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo/:pathExample", Method.GET, state, Void.class, new GetEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .paramDescription("pathExample", "An example path param")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode parameters = spec.get("paths").get("/openapi/echo/{pathExample}").get("get").get("parameters");

        boolean found = false;
        for (JsonNode param : parameters) {
            if ("pathExample".equals(param.get("name").asText())) {
                assertEquals("An example path param", param.get("description").asText());
                found = true;
            }
        }
        assertTrue("Should have pathExample parameter", found);
    }

    @Test
    public void shouldHideRoutesMarkedAsHidden() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.jsonRoute("/openapi/hidden", Method.GET, state, Void.class, new GetEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .hidden()
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode paths = spec.get("paths");

        assertNotNull(paths.get("/echo"));
        assertNull(paths.get("/openapi/hidden"));
        assertNull(paths.get("/openapi.json"));
    }

    @Test
    public void shouldGenerateOperationIds() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode operation = spec.get("paths").get("/echo").get("post");

        assertNotNull(operation.get("operationId"));
        assertEquals("post_echo", operation.get("operationId").asText());
    }

    @Test
    public void shouldMarkPrimitiveFieldsAsRequired() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                    new RouteConfigBuilder()
                            .openApi()
                            .summary("Echo the input")
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        JsonNode spec = getSpec(luxisTestClient);
        JsonNode schema = spec.get("paths").get("/openapi/echo").get("post")
                .get("requestBody").get("content").get("application/json").get("schema");

        JsonNode required = schema.get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        boolean hasIntExample = false;
        for (JsonNode node : required) {
            if ("intExample".equals(node.asText())) {
                hasIntExample = true;
            }
        }
        assertTrue("intExample should be in required", hasIntExample);
    }

    @Test
    public void shouldServeSpecViaEndpoint() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(StubRequest.request("/openapi.json"));

        assertEquals(200, response.statusCode);
        assertNotNull(response.responseBody);
        assertTrue(response.responseBody.contains("\"openapi\""));
        assertTrue(response.responseBody.contains("3.0.3"));
        assertTrue(response.responseBody.contains("Test API"));
    }

    @Test
    public void shouldChainRouteConfigWithTimeoutAndOpenApi() throws Exception {
        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/openapi/timeout", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                    new RouteConfigBuilder()
                            .timeout(5000)
                            .openApi()
                            .summary("Echo with timeout")
                            .done()
                            .build()
            );
            r.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");
        });

        JsonNode spec = getSpec(testClientAndServer.client());
        assertEquals("Echo with timeout", spec.get("paths").get("/openapi/timeout").get("post").get("summary").asText());
    }
}
