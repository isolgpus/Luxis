package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.http.client.HttpClientRequest;
import io.kiw.luxis.web.http.client.HttpClientResponse;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.http.client.LuxisHttpClientConfig;
import io.kiw.luxis.web.pipeline.WebSocketRoutesRegister;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.AlwaysThrowHandler;
import io.kiw.luxis.web.test.handler.EchoWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ErrorHandler;
import io.kiw.luxis.web.test.handler.FileDownloaderHandler;
import io.kiw.luxis.web.test.handler.FileUploadResponse;
import io.kiw.luxis.web.test.handler.FileUploaderHandler;
import io.kiw.luxis.web.test.handler.HttpClientCallHandler;
import io.kiw.luxis.web.test.handler.HttpClientGetRequest;
import io.kiw.luxis.web.test.handler.HttpClientPostCallHandler;
import io.kiw.luxis.web.test.handler.HttpClientPostRequest;
import io.kiw.luxis.web.test.handler.HttpClientTypedGetHandler;
import io.kiw.luxis.web.test.handler.SimpleGetHandler;
import io.kiw.luxis.web.test.handler.SimpleMultiplyHandler;
import io.kiw.luxis.web.test.handler.SimplePostValueHandler;
import io.kiw.luxis.web.test.handler.SimpleValueRequest;
import io.kiw.luxis.web.test.handler.SimpleWebsocketRequest;
import io.kiw.luxis.web.test.handler.WebSocketEchoRequest;
import io.kiw.luxis.web.test.handler.WebSocketEchoResponse;
import io.kiw.luxis.web.websocket.ClientWebSocketRoutes;
import io.kiw.luxis.web.websocket.WebSocketSession;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static io.kiw.luxis.web.application.routes.Eventually.eventually;
import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class HttpClientTest {

    private static final String SERVER_B_HOST = "127.0.0.1";
    private static final int SERVER_B_PORT = 8081;
    private static final String SERVER_B_BASE_URL = "http://" + SERVER_B_HOST + ":" + SERVER_B_PORT;
    private TestClientAndServer testClientAndServer;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer serverA;
    private TestClientAndServer serverB;

    public HttpClientTest(TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void assumeMode() {
        if (mode == TestMode.REAL) {
            assumeRealModeEnabled();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (serverA != null) {
            serverA.close();
        }
        if (serverB != null) {
            serverB.close();
        }
    }

    @Test
    public void shouldCallServerBViaHttpClientGet() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(42));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClient httpClient = creator.createHttpClient(mode);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-b", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, SERVER_B_BASE_URL));
            return state;
        }));

        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/call-b")
                        .body(json().put("targetPath", "/api/value").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("statusCode", 200)
                        .put("body", json().put("result", 42).toString())
                        .toString()),
                response);
    }

    @Test
    public void shouldForwardPostBodyToServerB() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/multiply", Method.POST, state, SimpleValueRequest.class, new SimpleMultiplyHandler());
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClient httpClient = creator.createHttpClient(mode);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/forward", Method.POST, state, HttpClientPostRequest.class, new HttpClientPostCallHandler(httpClient, "127.0.0.1:" + SERVER_B_PORT));
            return state;
        }));

        final String bodyForB = json().put("value", 7).toString();
        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/forward")
                        .body(json()
                                .put("targetPath", "/api/multiply")
                                .put("forwardBody", bodyForB)
                                .toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("statusCode", 200)
                        .put("body", json().put("result", 70).toString())
                        .toString()),
                response);
    }

    @Test
    public void shouldCallServerBUsingBaseUrl() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(99));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .baseUrl(SERVER_B_BASE_URL);
        final LuxisHttpClient httpClient = creator.createHttpClient(mode, config);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-b", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, ""));
            return state;
        }));

        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/call-b")
                        .body(json().put("targetPath", "/api/value").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("statusCode", 200)
                        .put("body", json().put("result", 99).toString())
                        .toString()),
                response);
    }

    @Test
    public void shouldReturnResultErrorWhenErrorAwareAndServerBReturns400() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/error", Method.GET, state, Void.class,
                                new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .errorAwareResponses(true);
        final LuxisHttpClient httpClient = creator.createHttpClient(mode, config);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-error", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, SERVER_B_BASE_URL));
            return state;
        }));

        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/call-error")
                        .body(json().put("targetPath", "/api/error").toString()));

        Assert.assertEquals(400, response.statusCode);
        Assert.assertEquals(
                json().put("message", "bad input").set("errors", json()).toString(),
                response.responseBody);
    }

    @Test
    public void shouldDeserializeTypedResponseFromServerB() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(42));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults();
        final LuxisHttpClient httpClient = creator.createHttpClient(mode, config);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-b-typed", Method.POST, state, HttpClientGetRequest.class, new HttpClientTypedGetHandler(httpClient, SERVER_B_BASE_URL));
            return state;
        }));

        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/call-b-typed")
                        .body(json().put("targetPath", "/api/value").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("result", 42)
                        .toString()),
                response);
    }

    @Test
    public void shouldHandleServerBReturningError() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/error", Method.GET, state, Void.class,
                                new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClient httpClient = creator.createHttpClient(mode);

        serverA = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/call-error", Method.POST, state, HttpClientGetRequest.class, new HttpClientCallHandler(httpClient, SERVER_B_BASE_URL));
            return state;
        }));

        final TestHttpResponse response = serverA.client().post(
                StubRequest.request("/call-error")
                        .body(json().put("targetPath", "/api/error").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("statusCode", 400)
                        .put("body", json()
                                .put("message", "bad input")
                                .set("errors", json())
                                .toString())
                        .toString()),
                response);
    }

    // --- Direct LuxisHttpClient tests against a single server ---

    private LuxisHttpClient createDirectClient() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/api/value", Method.GET, state, Void.class, new SimpleGetHandler(42));
            r.jsonRoute("/api/value", Method.POST, state, Void.class, new SimpleGetHandler(42));
            r.jsonRoute("/api/value", Method.PUT, state, Void.class, new SimpleGetHandler(42));
            r.jsonRoute("/api/value", Method.DELETE, state, Void.class, new SimpleGetHandler(42));
            r.jsonRoute("/api/value", Method.PATCH, state, Void.class, new SimpleGetHandler(42));

            r.jsonRoute("/api/postValue", Method.POST, state, ValueRequest.class, new SimplePostValueHandler());
            r.jsonRoute("/api/postValue", Method.PUT, state, ValueRequest.class, new SimplePostValueHandler());
            r.jsonRoute("/api/postValue", Method.PATCH, state, ValueRequest.class, new SimplePostValueHandler());

            r.jsonRoute("/api/bad-request", Method.GET, state, Void.class, new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            r.jsonRoute("/api/bad-request", Method.POST, state, Void.class, new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            r.jsonRoute("/api/bad-request", Method.PUT, state, Void.class, new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            r.jsonRoute("/api/bad-request", Method.DELETE, state, Void.class, new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));
            r.jsonRoute("/api/bad-request", Method.PATCH, state, Void.class, new ErrorHandler(ErrorStatusCode.BAD_REQUEST, "bad input"));

            r.jsonRoute("/api/throw", Method.GET, state, Void.class, new AlwaysThrowHandler());
            r.jsonRoute("/api/throw", Method.POST, state, Void.class, new AlwaysThrowHandler());
            r.jsonRoute("/api/throw", Method.PUT, state, Void.class, new AlwaysThrowHandler());
            r.jsonRoute("/api/throw", Method.DELETE, state, Void.class, new AlwaysThrowHandler());
            r.jsonRoute("/api/throw", Method.PATCH, state, Void.class, new AlwaysThrowHandler());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .baseUrl(SERVER_B_BASE_URL)
                .errorAwareResponses(true);
        return creator.createHttpClient(mode, config);
    }

    // GET

    @Test
    public void shouldGetSuccessfulResponseDirectly() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get("/api/value").toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldGetSuccessfulResponseDirectlyWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get(HttpClientRequest.request("/api/value"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldGetReturn400WhenValidationFailsWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get(HttpClientRequest.request("/api/bad-request"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldGetReturn500WhenExceptionThrownWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get(HttpClientRequest.request("/api/throw"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldGetReturn400WhenValidationFails() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get("/api/bad-request").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldGetReturn500WhenExceptionThrown() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.get("/api/throw").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    // POST

    @Test
    public void shouldPostSuccessfulResponseDirectly() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.post("/api/value", "{}").toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldPostSuccessfulResponseDirectlyWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<ValueResponse>> result = client.post(HttpClientRequest.request("/api/postValue", new ValueRequest("cheese")), ValueResponse.class)
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(new ValueResponse("cheese string"), success.body());
                });
    }

    @Test
    public void shouldPostReturn400WhenValidationFailsWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.post(HttpClientRequest.request("/api/bad-request", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPostReturn500WhenExceptionThrownWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.post(HttpClientRequest.request("/api/throw", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPostReturn400WhenValidationFails() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.post("/api/bad-request", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPostReturn500WhenExceptionThrown() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.post("/api/throw", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    // PUT

    @Test
    public void shouldPutSuccessfulResponseDirectly() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.put("/api/value", "{}").toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldPutSuccessfulResponseDirectlyWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<ValueResponse>> result = client.put(HttpClientRequest.request("/api/postValue", new ValueRequest("cheese")), ValueResponse.class)
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(new ValueResponse("cheese string"), success.body());
                });
    }

    @Test
    public void shouldPutReturn400WhenValidationFailsWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.put(HttpClientRequest.request("/api/bad-request", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPutReturn500WhenExceptionThrownWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.put(HttpClientRequest.request("/api/throw", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPutReturn400WhenValidationFails() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.put("/api/bad-request", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPutReturn500WhenExceptionThrown() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.put("/api/throw", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    // DELETE

    @Test
    public void shouldDeleteSuccessfulResponseDirectly() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete("/api/value").toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode() + " " + error.errorMessageValue().message()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldDeleteSuccessfulResponseDirectlyWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete(HttpClientRequest.request("/api/value"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldDeleteReturn400WhenValidationFailsWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete(HttpClientRequest.request("/api/bad-request"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldDeleteReturn500WhenExceptionThrownWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete(HttpClientRequest.request("/api/throw"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldDeleteReturn400WhenValidationFails() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete("/api/bad-request").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldDeleteReturn500WhenExceptionThrown() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.delete("/api/throw").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    // PATCH

    @Test
    public void shouldPatchSuccessfulResponseDirectly() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.patch("/api/value", "{}").toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(json().put("result", 42).toString(), success.body());
                });
    }

    @Test
    public void shouldPatchSuccessfulResponseDirectlyWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<ValueResponse>> result = client.patch(HttpClientRequest.request("/api/postValue", new ValueRequest("cheese")), ValueResponse.class)
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(new ValueResponse("cheese string"), success.body());
                });
    }

    @Test
    public void shouldPatchReturn400WhenValidationFailsWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.patch(HttpClientRequest.request("/api/bad-request", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPatchReturn500WhenExceptionThrownWithHttpClientRequestApi() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.patch(HttpClientRequest.request("/api/throw", "{}"), String.class)
                .toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPatchReturn400WhenValidationFails() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.patch("/api/bad-request", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(400, error.statusCode());
                    Assert.assertEquals("bad input", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldPatchReturn500WhenExceptionThrown() {
        final LuxisHttpClient client = createDirectClient();
        final Result<HttpErrorResponse, HttpClientResponse<String>> result = client.patch("/api/throw", "{}").toCompletableFuture().join();

        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                success -> Assert.fail("Expected error but got success: " + success.statusCode()));
    }

    @Test
    public void shouldCreateWebsocketConnection() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));
        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .baseUrl(SERVER_B_BASE_URL);
        final LuxisHttpClient client = creator.createHttpClient(mode, config);
        final AtomicReference<WebSocketEchoResponse> received = new AtomicReference<>();
        final WebSocketSession<SimpleWebsocketRequest> session = client.connectToWebSocket("/ws/echo",
                new ClientWebSocketRoutes<ClientState, SimpleWebsocketRequest>() {

                    @Override
                    public void registerRoutes(final WebSocketRoutesRegister<ClientState, SimpleWebsocketRequest> routesRegister) {
                        routesRegister.registerInbound("echoResponse", WebSocketEchoResponse.class, stream ->
                                stream.peek(ctx -> received.set(ctx.in()))
                                        .completeWithNoResponse());

                        routesRegister.registerOutbound("echo", WebSocketEchoRequest.class);
                    }
                });

        final WebSocketEchoRequest request = new WebSocketEchoRequest();
        request.message = "hello";
        session.send(request);

        eventually(mode, () -> {
            Assert.assertNotNull(received.get());
            Assert.assertEquals("echo: hello", received.get().echo());
        });
    }

    // FILE UPLOAD

    @Test
    public void shouldUploadFilesViaHttpClient() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.uploadFileRoute("/upload", Method.POST, state, new FileUploaderHandler());
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .baseUrl(SERVER_B_BASE_URL);
        final LuxisHttpClient client = creator.createHttpClient(mode, config);

        final Result<HttpErrorResponse, HttpClientResponse<FileUploadResponse>> result = client.postFiles(
                HttpClientRequest.request("/upload")
                        .fileUpload("file1", "some bytes")
                        .fileUpload("file2", "even more bytes"),
                FileUploadResponse.class
        ).toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals(10, (int) success.body().results().get("file1"));
                    Assert.assertEquals(15, (int) success.body().results().get("file2"));
                });
    }

    // FILE DOWNLOAD

    @Test
    public void shouldDownloadFileViaHttpClient() {
        serverB = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.downloadFileRoute("/download", Method.GET, state, new FileDownloaderHandler(), "text/html; charset=utf-8");
            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(SERVER_B_PORT).build()));

        final LuxisHttpClientConfig config = LuxisHttpClientConfig.defaults()
                .baseUrl(SERVER_B_BASE_URL);
        final LuxisHttpClient client = creator.createHttpClient(mode, config);

        final Result<HttpErrorResponse, HttpClientResponse<HttpBuffer>> result = client.download("/download")
                .toCompletableFuture().join();

        result.consume(
                error -> Assert.fail("Expected success but got error: " + error.statusCode()),
                success -> {
                    Assert.assertEquals(200, success.statusCode());
                    Assert.assertEquals("file contents", new String(success.body().bytes(), java.nio.charset.StandardCharsets.UTF_8));
                    Assert.assertEquals("data.txt", success.headers().get("Content-Disposition"));
                    Assert.assertEquals("text/html; charset=utf-8", success.headers().get("Content-Type"));
                });
    }

    private class ClientState {
    }
}
