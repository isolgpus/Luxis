package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.HttpCookie;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestFilter;
import io.kiw.luxis.web.test.TestHelper;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.BlockingCompleteTestHandler;
import io.kiw.luxis.web.test.handler.BlockingFlatMapFailHandler;
import io.kiw.luxis.web.test.handler.BlockingRequest;
import io.kiw.luxis.web.test.handler.BlockingTestHandler;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.ErrorFilter;
import io.kiw.luxis.web.test.handler.FailingTestHandler;
import io.kiw.luxis.web.test.handler.FileDownloaderHandler;
import io.kiw.luxis.web.test.handler.FileUploaderHandler;
import io.kiw.luxis.web.test.handler.GetEchoHandler;
import io.kiw.luxis.web.test.handler.GetTestFilterHandler;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import io.kiw.luxis.web.test.handler.StateTestHandler;
import io.kiw.luxis.web.test.handler.TestFilterHandler;
import io.kiw.luxis.web.test.handler.TestFilterRequest;
import io.kiw.luxis.web.test.handler.ThrowRequest;
import io.kiw.luxis.web.test.handler.ThrowTestHandler;
import io.kiw.luxis.web.test.handler.ValidationRequest;
import io.kiw.luxis.web.test.handler.ValidationTestHandler;
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
public class JsonHttpInfrastructureTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;
    private static final String DEFAULT_POST_RESPONSE = json()
            .put("intExample", 0)
            .putNull("stringExample")
            .putNull("pathExample")
            .putNull("queryExample")
            .putNull("requestHeaderExample")
            .putNull("requestCookieExample")
            .toString();

    public JsonHttpInfrastructureTest(String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() throws Exception {
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
    public void shouldHandlePopulatingJsonValues() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        final String requestBody = json()
                .put("intExample", 17)
                .put("stringExample", "hiya")
                .toString();

        TestHttpResponse response = luxisTestClient.post(StubRequest.request("/echo").body(requestBody));

        final String expectedResponse = json()
                .put("intExample", 17)
                .put("stringExample", "hiya")
                .putNull("pathExample")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadQueryParamsInPost() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.PUT, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.put(
                StubRequest.request("/echo")
                        .body("{}")
                        .queryParam("queryExample", "hi"));

        final String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadRequestHeaderParamsOnPost() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/echo")
                        .body("{}")
                        .queryParam("queryExample", "hi")
                        .headerParam("requestHeaderExample", "test"));

        final String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .put("requestHeaderExample", "test")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadRequestHeaderParamsOnPut() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.PUT, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.put(
                StubRequest.request("/echo")
                        .body("{}")
                        .queryParam("queryExample", "hi")
                        .headerParam("requestHeaderExample", "test"));

        final String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .put("requestHeaderExample", "test")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadRequestHeaderParamsOnDelete() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.DELETE, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.delete(
                StubRequest.request("/echo")
                        .body("{}")
                        .queryParam("queryExample", "hi")
                        .headerParam("requestHeaderExample", "test"));

        final String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .put("requestHeaderExample", "test")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadRequestHeaderParamsOnPatch() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.PATCH, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.patch(
                StubRequest.request("/echo")
                        .body("{}")
                        .queryParam("queryExample", "hi")
                        .headerParam("requestHeaderExample", "test"));

        final String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .put("requestHeaderExample", "test")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadRequestHeaderParamsOnGet() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(
                StubRequest.request("/echo")
                        .queryParam("queryExample", null)
                        .headerParam("requestHeaderExample", "test"));

        final String expectedResponse = json()
                .put("intExample", 188)
                .put("stringExample", "You invoked a GET")
                .putNull("pathExample")
                .putNull("queryExample")
                .put("requestHeaderExample", "test")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }

    @Test
    public void shouldReadQueryParamsInGet() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(
                StubRequest.request("/echo")
                        .queryParam("queryExample", "hi"));

        final String expectedResponse = json()
                .put("intExample", 188)
                .put("stringExample", "You invoked a GET")
                .putNull("pathExample")
                .put("queryExample", "hi")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response);
    }


    @Test
    public void shouldIgnoreWhenClientSendsUnknownValues() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        final String requestBody = json()
                .put("intExample", 17)
                .put("stringExample", "hiya")
                .putNull("pathExample")
                .put("something", "else")
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/echo")
                        .body(requestBody));

        final String expectedResponse = json()
                .put("intExample", 17)
                .put("stringExample", "hiya")
                .putNull("pathExample")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }

    @Test
    public void shouldRespondWithErrorNicelyWhenRequestBodyIsNotPresentOnPost() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(StubRequest.request("/echo"));

        final String expectedResponse = json()
                .put("message", "Invalid json request")
                .set("errors", json())
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponse).withStatusCode(400), response);
    }

    @Test
    public void shouldCallGetRoute() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(StubRequest.request("/echo"));

        final String expectedResponseBody = json()
                .put("intExample", 188)
                .put("stringExample", "You invoked a GET")
                .putNull("pathExample")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expectedResponseBody), response);
    }

    @Test
    public void shouldPopulateResponseHeaders() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        final String request = json()
                .put("responseHeaderExample", "responseTest")
                .toString();

        TestHttpResponse response = luxisTestClient.post(StubRequest.request("/echo")
                .body(request));

        Assert.assertEquals(TestHttpResponse.response(DEFAULT_POST_RESPONSE)
                .withHeader("responseHeaderExample", "responseTest"), response);
    }


    @Test
    public void shouldReadRequestCookies() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/echo")
                        .body("{}")
                        .cookie("requestCookieExample", "cookietest"));

        String expectedResponse = json()
                .put("intExample", 0)
                .putNull("stringExample")
                .putNull("pathExample")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .put("requestCookieExample", "cookietest")
                .toString();
        Assert.assertEquals(TestHttpResponse.response(expectedResponse), response);
    }

    @Test
    public void shouldPopulateResponseCookie() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/echo")
                        .body(json().put("responseCookieExample", "responseCookieTest").toString()));

        Assert.assertEquals(TestHttpResponse.response(DEFAULT_POST_RESPONSE)
                .withCookie(new HttpCookie("responseCookieExample", "responseCookieTest")), response);
    }

    @Test
    public void shouldMapThroughABlockingCall() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/blocking", Method.POST, state, BlockingRequest.class, new BlockingTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/blocking")
                        .body(json().put("numberToMultiply", 22).toString()));

        Assert.assertEquals(TestHttpResponse.response(json().put("multipliedNumber", 44).toString()), response);
    }

    @Test
    public void shouldMapThroughABlockingCompleteCall() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/blockingComplete", Method.POST, state, BlockingRequest.class, new BlockingCompleteTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/blockingComplete")
                        .body(json().put("numberToMultiply", 22).toString()));

        Assert.assertEquals(TestHttpResponse.response(json().put("multipliedNumber", 44).toString()), response);
    }

    @Test
    public void shouldReturnWithErrorOnBadRequest() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/failing", Method.POST, state, BlockingRequest.class, new FailingTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/failing")
                        .body(json().put("numberToMultiply", 22).toString()));

        Assert.assertEquals(TestHttpResponse.response(json()
                .put("message", "intentionally failed")
                .set("errors", json())
                .toString()).withStatusCode(400), response);
    }


    @Test
    public void shouldAccessRequestBodyInHandlerWhenRoutedThroughFilter() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String requestBody = json()
                .put("intExample", 42)
                .put("stringExample", "through filter")
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/root/filter/echo").body(requestBody));

        String expectedResponse = json()
                .put("intExample", 42)
                .put("stringExample", "through filter")
                .putNull("pathExample")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse)
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response);
    }

    @Test
    public void shouldApplyFilterBeforeHandle() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.POST, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/root/filter/test")
                        .body(json().toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("filterMessage", "hit handler").toString())
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response
        );
    }

    @Test
    public void shouldApplyFilterBeforeHandleOnGet() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.GET, state, Void.class, new GetTestFilterHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(
                StubRequest.request("/root/filter/test"));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("filterMessage", "hit handler").toString())
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response
        );
    }

    @Test
    public void shouldApplyFilterBeforeHandleOnPut() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.PUT, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.put(
                StubRequest.request("/root/filter/test")
                        .body(json().toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("filterMessage", "hit handler").toString())
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response
        );
    }

    @Test
    public void shouldApplyFilterBeforeHandleOnDelete() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.DELETE, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.delete(
                StubRequest.request("/root/filter/test")
                        .body(json().toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("filterMessage", "hit handler").toString())
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response
        );
    }

    @Test
    public void shouldApplyFilterBeforeHandleOnPatch() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
            r.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
            r.jsonRoute("/root/filter/test", Method.PATCH, state, TestFilterRequest.class, new TestFilterHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.patch(
                StubRequest.request("/root/filter/test")
                        .body(json().toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("filterMessage", "hit handler").toString())
                        .withCookie(new HttpCookie("rootFilter", "hitfilter"))
                        .withCookie(new HttpCookie("pathFilter", "hitfilter")),
                response
        );
    }

    @Test
    public void shouldHandleMalformedJsonRequest() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/throw", Method.POST, state, ThrowRequest.class, new ThrowTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/throw")
                        .body("<not json at all>"));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "Invalid json request")
                        .set("errors", json())
                        .toString()).withStatusCode(400),
                response
        );
    }

    @Test
    public void shouldHandleItWhenThrowingAnExceptionWithinTheHandler() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/throw", Method.POST, state, ThrowRequest.class, new ThrowTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/throw")
                        .body(json().put("where", "complete").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response
        );

        luxisTestClient.assertException("app error in complete");
    }

    @Test
    public void shouldHandleItWhenThrowingAnExceptionInMapHandler() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/throw", Method.POST, state, ThrowRequest.class, new ThrowTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/throw")
                        .body(json().put("where", "map").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response
        );

        luxisTestClient.assertException("app error in map");
    }

    @Test
    public void shouldHandleItWhenThrowingAnExceptionInBlockingHandler() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/throw", Method.POST, state, ThrowRequest.class, new ThrowTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/throw")
                        .body(json().put("where", "blocking").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response
        );

        luxisTestClient.assertException("app error in blocking");
    }

    @Test
    public void shouldUploadAFile() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.uploadFileRoute("/upload", Method.POST, state, new FileUploaderHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/upload")
                        .fileUpload("file1", "some bytes")
                        .fileUpload("file2", "even more bytes"));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .set("results", json().put("file1", 10).put("file2", 15))
                        .toString()),
                response
        );
    }

    @Test
    public void shouldDownloadFile() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.downloadFileRoute("/download", Method.GET, state, new FileDownloaderHandler(), "text/html; charset=utf-8");

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(StubRequest.request("/download"));

        Assert.assertEquals(
                TestHttpResponse.response(TestHelper.file("file contents"), "text/html; charset=utf-8")
                        .withHeader("Transfer-Encoding", "chunked")
                        .withHeader("Content-Disposition", "data.txt"),
                response
        );
    }


    @Test
    public void shouldSupportPathParam() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/echo/:pathExample", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(
                StubRequest.request("/echo/myvariable"));

        final String expectedResponse = json()
                .put("intExample", 188)
                .put("stringExample", "You invoked a GET")
                .put("pathExample", "myvariable")
                .putNull("queryExample")
                .putNull("requestHeaderExample")
                .putNull("requestCookieExample")
                .toString();

        Assert.assertEquals(
                TestHttpResponse.response(expectedResponse),
                response
        );
    }

    @Test
    public void shouldPassValidationAndReturnResponse() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/42")
                        .body(body)
                        .queryParam("page", "1"));

        String expected = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .put("city", "NYC")
                .put("page", "1")
                .put("userId", "42")
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected), response);
    }

    @Test
    public void shouldReturnValidationErrorForInvalidBodyField() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .putNull("name")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/42")
                        .body(body)
                        .queryParam("page", "1"));

        String expected = json()
                .put("message", "Validation failed")
                .set("errors", json()
                        .set("name", TestHelper.MAPPER.createArrayNode().add("must not be blank")))
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected).withStatusCode(422), response);
    }

    @Test
    public void shouldReturnValidationErrorForInvalidEmail() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .put("name", "Alice")
                .put("email", "not-an-email")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/42")
                        .body(body)
                        .queryParam("page", "1"));

        String expected = json()
                .put("message", "Validation failed")
                .set("errors", json()
                        .set("email", TestHelper.MAPPER.createArrayNode().add("must be a valid email address")))
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected).withStatusCode(422), response);
    }

    @Test
    public void shouldReturnValidationErrorForNestedField() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "bad"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/42")
                        .body(body)
                        .queryParam("page", "1"));

        String expected = json()
                .put("message", "Validation failed")
                .set("errors", json()
                        .set("address.zip", TestHelper.MAPPER.createArrayNode().add("must match pattern: [0-9]{5}")))
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected).withStatusCode(422), response);
    }

    @Test
    public void shouldReturnValidationErrorForMissingQueryParam() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/42").body(body));

        String expected = json()
                .put("message", "Validation failed")
                .set("errors", json()
                        .set("page", TestHelper.MAPPER.createArrayNode().add("must not be blank")))
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected).withStatusCode(422), response);
    }

    @Test
    public void shouldReturnApplicationState() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/state", Method.POST, state, Void.class, new StateTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(StubRequest.request("/state").body("{}"));

        Assert.assertEquals(TestHttpResponse.response(json().put("longValue", 55).toString()), response);
    }

    @Test
    public void shouldShortCircuitWhenFilterReturnsError() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonFilter("/protected/*", state, new ErrorFilter());
            r.jsonRoute("/protected/resource", Method.GET, state, Void.class, new GetEchoHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.get(StubRequest.request("/protected/resource"));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "filter blocked")
                        .set("errors", json())
                        .toString()).withStatusCode(401),
                response
        );
    }

    @Test
    public void shouldHandleBlockingFlatMapFailure() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/blockingFailing", Method.POST, state, BlockingRequest.class, new BlockingFlatMapFailHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/blockingFailing").body(json().put("numberToMultiply", 5).toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "blocking flat map failed")
                        .set("errors", json())
                        .toString()).withStatusCode(400),
                response
        );
    }

    @Test
    public void shouldReturnValidationErrorForInvalidPathParam() {
        testClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());

            return state;
        }));
        TestClient luxisTestClient = testClientAndServer.client();

        String body = json()
                .put("name", "Alice")
                .put("email", "alice@example.com")
                .put("age", 25)
                .set("address", json().put("city", "NYC").put("zip", "10001"))
                .toString();

        TestHttpResponse response = luxisTestClient.post(
                StubRequest.request("/validate/not-a-number")
                        .body(body)
                        .queryParam("page", "1"));

        String expected = json()
                .put("message", "Validation failed")
                .set("errors", json()
                        .set("userId", TestHelper.MAPPER.createArrayNode().add("must match pattern: [0-9]+")))
                .toString();

        Assert.assertEquals(TestHttpResponse.response(expected).withStatusCode(422), response);
    }
}
