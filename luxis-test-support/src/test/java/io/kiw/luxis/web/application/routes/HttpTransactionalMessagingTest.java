package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.InMemoryOutboxStore;
import io.kiw.luxis.web.test.InMemoryPublisher;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.EchoResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.test.TestHelper.json;

@RunWith(Parameterized.class)
public class HttpTransactionalMessagingTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer testClientAndServer;

    public HttpTransactionalMessagingTest(final String mode) {
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
    public void shouldBufferEventsInsideTxAppendBeforeCommitAndDispatchAfter() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new MultiPayloadTransactionalHandler());
        }, tm, publisher, outbox);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(200, response.statusCode);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "update:1:insert into users values (?)",
                "commit:1"), tm.events());

        Assert.assertEquals(Arrays.asList(
                "append:1:3",
                "readPending:3",
                "markBatchSent:3"), outbox.events());

        Assert.assertEquals(Arrays.asList(
                "publishBatch:3",
                "publish:str:1:order:hello-str",
                "publish:bytes:2:order:hello-bytes",
                "publish:buf:3:order:hello-buf"), publisher.events());

        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldRollbackWhenOutboxAppendFails() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore().failAppends();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new MultiPayloadTransactionalHandler());
        }, tm, publisher, outbox);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(500, response.statusCode);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "update:1:insert into users values (?)",
                "rollback:1"), tm.events());

        Assert.assertEquals(List.of("append:1:3"), outbox.events());

        Assert.assertTrue("publisher must not see events when append failed",
                publisher.events().isEmpty());

        client.assertException("append failed");
        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldDispatchOutsideTransactionViaOutbox() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/publish", Method.POST, state, EchoRequest.class, new OutsideTxPublishHandler());
        }, tm, publisher, outbox);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/publish").body(json().put("stringExample", "outside").toString()));

        Assert.assertEquals(200, response.statusCode);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "commit:1"), tm.events());

        Assert.assertEquals(Arrays.asList(
                "append:1:1",
                "readPending:1",
                "markBatchSent:1"), outbox.events());

        Assert.assertEquals(Arrays.asList(
                "publishBatch:1",
                "publish:str:1:topic:outside"), publisher.events());

        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldAppendButNotDrainWhenDrainerIsDisabled() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore().disableDrainer();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new MultiPayloadTransactionalHandler());
        }, tm, publisher, outbox);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(200, response.statusCode);

        Assert.assertEquals(Arrays.asList(
                "begin:1",
                "update:1:insert into users values (?)",
                "commit:1"), tm.events());

        Assert.assertEquals(List.of("append:1:3"), outbox.events());

        Assert.assertTrue("publisher must not see events when drainer is disabled",
                publisher.events().isEmpty());

        client.assertNoMoreExceptions();
    }

    @Test
    public void shouldFailWhenInsideTxPublishHasNoOutboxRegistered() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();

        testClientAndServer = creator.createTestServerAndClient(mode, (r, state) -> {
            r.jsonRoute("/tx", Method.POST, state, EchoRequest.class, new MultiPayloadTransactionalHandler());
        }, tm);
        final TestClient client = testClientAndServer.client();

        final TestHttpResponse response = client.post(
                StubRequest.request("/tx").body(json().put("stringExample", "hello").toString()));

        Assert.assertEquals(500, response.statusCode);
        client.assertException("OutboxStore");
        client.assertNoMoreExceptions();
    }

    static final class MultiPayloadTransactionalHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {
        @Override
        public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
            return e.map(ctx -> ctx.in().stringExample)
                    .inTransaction(tx -> tx
                            .asyncPeek(ctx -> {
                                ctx.publisher().publish("order", ctx.in() + "-str");
                                ctx.publisher().publish("order", (ctx.in() + "-bytes").getBytes(StandardCharsets.UTF_8));
                                ctx.publisher().publish("order", ByteBuffer.wrap((ctx.in() + "-buf").getBytes(StandardCharsets.UTF_8)));
                                return ctx.db().update("insert into users values (?)", ctx.in());
                            })
                            .commit())
                    .complete(ctx -> HttpResult.success(new EchoResponse(0, ctx.in(), null, null, null, null)));
        }
    }

    static final class OutsideTxPublishHandler implements JsonHandler<EchoRequest, EchoResponse, MyApplicationState> {
        @Override
        public LuxisPipeline<EchoResponse> handle(final HttpStream<EchoRequest, MyApplicationState> e) {
            return e.map(ctx -> ctx.in().stringExample)
                    .<String>asyncMap(ctx -> {
                        final String value = ctx.in();
                        return ctx.publisher().publish("topic", value).map(v -> value);
                    })
                    .complete(ctx -> HttpResult.success(new EchoResponse(0, ctx.in(), null, null, null, null)));
        }
    }
}
