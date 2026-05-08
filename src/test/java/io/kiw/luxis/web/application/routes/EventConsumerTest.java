package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.InMemoryEventConsumer;
import io.kiw.luxis.web.test.InMemoryOutboxStore;
import io.kiw.luxis.web.test.InMemoryPublisher;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestHttpResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.REAL_MODE;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.assumeRealModeEnabled;
import static io.kiw.luxis.web.application.routes.TestApplicationClientCreator.createTestServerAndClient;

@RunWith(Parameterized.class)
public class EventConsumerTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final String mode;
    private TestClientAndServer publisherTestClientAndServer;
    private TestClientAndServer consumerTestClientAndServer;

    public EventConsumerTest(final String mode) {
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
        if (consumerTestClientAndServer != null) {
            try {
                consumerTestClientAndServer.luxis().close();
            } catch (final Exception ignored) {
            }
        }
        if (publisherTestClientAndServer != null) {
            try {
                publisherTestClientAndServer.luxis().close();
            } catch (final Exception ignored) {
            }
        }
    }

    @Test
    public void shouldHandleEvent() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore();
        final AtomicReference<Animal> received = new AtomicReference<>();

        publisherTestClientAndServer = createTestServerAndClient(mode, 8081, (r, state) -> {
            r.jsonRoute("/publishToOther", Method.POST, new GenericAppState(), PublishTestRequest.class, new JsonHandler<PublishTestRequest, PublishTestResponse, GenericAppState>() {
                @Override
                public LuxisPipeline<PublishTestResponse> handle(final HttpStream<PublishTestRequest, GenericAppState> e) {
                    return e.inTransaction(tx -> tx.asyncMap(ctx -> {
                        ctx.publisher().publish("topic", "{\"type\":\"someKey\", \"payload\": {\"animal\":\"fish\"}}");
                        return AsyncTestSupport.completed(new PublishTestResponse());
                    }).commit()).complete();
                }
            });
        }, tm, publisher, outbox);

        consumerTestClientAndServer = createTestServerAndClient(mode, (r, state) -> {
            r.eventRoute("someKey", state, Animal.class, luxisStream -> luxisStream
                    .peek(ctx -> received.set(ctx.in()))
                    .complete());
        }, tm, publisher, outbox, new InMemoryEventConsumer("topic"));

        final TestClient client = publisherTestClientAndServer.client();
        final TestHttpResponse response = client.post(StubRequest.request("/publishToOther").body("{}"));

        Assert.assertEquals(200, response.statusCode);
        Assert.assertNotNull("expected the consumer to receive an Animal event", received.get());
        Assert.assertEquals("fish", received.get().getAnimal());
        client.assertNoMoreExceptions();
    }


}
