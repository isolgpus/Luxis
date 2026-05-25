package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.InMemoryEventConsumer;
import io.kiw.luxis.web.test.InMemoryOutboxStore;
import io.kiw.luxis.web.test.InMemoryPublisher;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestHttpResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import io.kiw.luxis.web.test.TestMode;
import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.test.MyApplicationState;

@RunWith(Parameterized.class)
public class EventConsumerTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer publisherTestClientAndServer;
    private TestClientAndServer consumerTestClientAndServer;

    public EventConsumerTest(final TestMode mode) {
        this.mode = mode;
    }

    @Before
    public void setUp() {
        if (mode == TestMode.REAL) {
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

        publisherTestClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/publishToOther", Method.POST, new GenericAppState(), PublishTestRequest.class,
                    e -> e.inTransaction(
                                    tx -> tx.asyncMap(
                                            ctx -> {
                                                ctx.publisher().publish("topic", "{\"type\":\"someKey\", \"payload\": {\"animal\":\"fish\"}}");
                                                return AsyncTestSupport.completed(new PublishTestResponse());
                                            }).commit())
                            .complete());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(8081).build()).withDatabase(tm).withEventPlatform(TestApplicationClientCreator.createEventPlatform(publisher, outbox, null)));

        consumerTestClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.eventRoute("someKey", state, Animal.class, luxisStream -> luxisStream
                    .peek(ctx -> received.set(ctx.in()))
                    .complete());

            return state;
        }).withDatabase(tm).withEventPlatform(TestApplicationClientCreator.createEventPlatform(publisher, outbox, new InMemoryEventConsumer("topic"))));

        final TestClient client = publisherTestClientAndServer.client();
        final TestHttpResponse response = client.post(StubRequest.request("/publishToOther").body("{}"));

        Assert.assertEquals(200, response.statusCode);
        Assert.assertNotNull("expected the consumer to receive an Animal event", received.get());
        Assert.assertEquals("fish", received.get().getAnimal());
        client.assertNoMoreExceptions();
    }


}
