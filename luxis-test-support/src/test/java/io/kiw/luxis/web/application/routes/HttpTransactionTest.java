package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.ChainedAsyncTransactionalHttpHandler;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.TransactionalHttpHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createContextAsserter;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createTestServerAndClient;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class HttpTransactionTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestClientAndServer testClientAndServer;

    public HttpTransactionTest(final String mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (REAL_MODE.equals(mode)) {
            assumeRealModeEnabled();
        }
    }

    @After
    public void tearDown() {
        if (testClientAndServer != null) {
            try {
                testClientAndServer.luxis().close();
            } catch (final Exception ignored) {
            }
        }
    }

    @Test
    public void shouldRunTransactionalSubChainAndFireCommitLifecycle() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new TransactionalHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("intExample", 0)
                        .put("stringExample", "hello-updated")
                        .putNull("pathExample")
                        .putNull("queryExample")
                        .putNull("requestHeaderExample")
                        .putNull("requestCookieExample")
                        .toString()),
                response);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "query:1:select * from users where username = ? FOR UPDATE",
                "update:1:insert into users values (?)",
                "commit:1",
                "onCommitted:1"), tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRunIndependentTransactionsForSequentialRequests() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new TransactionalHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        client.post(StubRequest.request("/tx").body(json().put("stringExample", "first").toString()));
        client.post(StubRequest.request("/tx").body(json().put("stringExample", "second").toString()));

        Assert.assertEquals(
                Arrays.asList(
                        "begin:1",
                        "query:1:select * from users where username = ? FOR UPDATE",
                        "update:1:insert into users values (?)",
                        "commit:1",
                        "onCommitted:1",
                        "begin:2",
                        "query:2:select * from users where username = ? FOR UPDATE",
                        "update:2:insert into users values (?)",
                        "commit:2",
                        "onCommitted:2"),
                tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenFlatMapReturnsResultError() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new RollbackOnErrorHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "sub-chain failed")
                        .set("errors", json())
                        .toString()).withStatusCode(400),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenAsyncMapReturnsFailedFuture() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new AsyncMapThrowsTransactionalHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertException("async driver failed");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenSyncMapThrows() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new MapThrowsTransactionalHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertException("sync map failed");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldSurfaceCommitFailureViaExceptionHandler() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient().failCommits();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new TransactionalHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "query:1:select * from users where username = ? FOR UPDATE",
                "update:1:insert into users values (?)",
                "commit:1"), tm.events());
        client.assertException("commit failed");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldSurfaceRollbackFailureViaExceptionHandler() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient().failRollbacks();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new RollbackOnErrorHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertException("rollback failed");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenAsyncMapReturnsTypedError() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new AsyncMapTypedErrorTransactionalHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("message", "async typed error")
                        .set("errors", json())
                        .toString()).withStatusCode(400),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenAsyncMapMapperThrowsSynchronously() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new AsyncMapSyncThrowTransactionalHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertException("async mapper threw");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldFlowFinalValueIntoOuterStepAfterTransaction() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new OuterStepAfterTransactionHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("intExample", 0)
                        .put("stringExample", "hello-tx-after")
                        .putNull("pathExample")
                        .putNull("queryExample")
                        .putNull("requestHeaderExample")
                        .putNull("requestCookieExample")
                        .toString()),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "commit:1"), tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRunTwoSequentialTransactionsWithinOneRequest() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new TwoTransactionsHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("intExample", 0)
                        .put("stringExample", "hello-tx1-tx2")
                        .putNull("pathExample")
                        .putNull("queryExample")
                        .putNull("requestHeaderExample")
                        .putNull("requestCookieExample")
                        .toString()),
                response);

        Assert.assertEquals(
                Arrays.asList("begin:1", "commit:1", "begin:2", "commit:2"),
                tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldChainTwoAsyncMapsAndCommit() {
        final ContextAsserter asserter = createContextAsserter(mode);
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new ChainedAsyncTransactionalHttpHandler(asserter));
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json()
                        .put("intExample", 0)
                        .put("stringExample", "hello-first-between-second")
                        .putNull("pathExample")
                        .putNull("queryExample")
                        .putNull("requestHeaderExample")
                        .putNull("requestCookieExample")
                        .toString()),
                response);

        Assert.assertEquals(
                Arrays.asList("begin:1", "commit:1", "onCommitted:1"),
                tm.events());
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenSecondChainedAsyncFails() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new ChainedAsyncSecondFailsTransactionalHttpHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(
                TestHttpResponse.response(json().put("message", "Something went wrong").toString()).withStatusCode(500),
                response);

        Assert.assertEquals(Arrays.asList("begin:1", "rollback:1"), tm.events());
        client.assertException("second async driver failed");
        client.assertNoMoreExceptions();
    }
}
