package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.pipeline.LoopConfig;
import io.kiw.luxis.web.pipeline.LoopStep;
import io.kiw.luxis.web.test.AsyncTestSupport;
import io.kiw.luxis.web.test.InMemoryDatabaseClient;
import io.kiw.luxis.web.test.InMemoryEventConsumer;
import io.kiw.luxis.web.test.InMemoryOutboxStore;
import io.kiw.luxis.web.test.InMemoryPublisher;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubRequest;
import io.kiw.luxis.web.test.TestApplicationClientCreator;
import io.kiw.luxis.web.test.TestClient;
import io.kiw.luxis.web.test.TestClientAndServer;
import io.kiw.luxis.web.test.TestHttpResponse;
import io.kiw.luxis.web.test.TestMode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.kiw.luxis.web.test.internal.RealModeAssumption.assumeRealModeEnabled;

@RunWith(Parameterized.class)
public class EventLoopTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> modes() {
        return TestApplicationClientCreator.modes();
    }

    private final TestMode mode;
    private final TestApplicationClientCreator creator = new TestApplicationClientCreator();
    private TestClientAndServer publisherTestClientAndServer;
    private TestClientAndServer consumerTestClientAndServer;

    public EventLoopTest(final TestMode mode) {
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
    public void shouldRunMultipleTurnsThenExitInEventConsumer() {
        final InMemoryDatabaseClient tm = new InMemoryDatabaseClient();
        final InMemoryPublisher publisher = new InMemoryPublisher();
        final InMemoryOutboxStore outbox = new InMemoryOutboxStore();
        final AtomicReference<Integer> result = new AtomicReference<>();
        final AtomicLong iterationCounter = new AtomicLong();

        publisherTestClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.jsonRoute("/publishToOther", Method.POST, new GenericAppState(), PublishTestRequest.class,
                    e -> e.inTransaction(
                                    tx -> tx.asyncMap(
                                            ctx -> {
                                                ctx.publisher().publish("topic", "{\"type\":\"loopEvent\", \"payload\": {\"target\":3}}");
                                                return AsyncTestSupport.completed(new PublishTestResponse());
                                            }).commit())
                            .complete());

            return state;
        }).withConfig(new WebServiceConfigBuilder().setPort(8081).build()).withDatabase(tm).withEventPlatform(TestApplicationClientCreator.createEventPlatform(publisher, outbox, null)));

        consumerTestClientAndServer = creator.createTestServerAndClient(mode, Luxis.app(r -> {
            final MyApplicationState state = new MyApplicationState();
            r.eventRoute("loopEvent", state, LoopEvent.class, stream -> stream
                    .map(ctx -> new Counter(0, ctx.in().getTarget()))
                    .<Integer>loop(LoopConfig.maxIterations(20), loop -> loop
                            .blockingMap(ctx -> {
                                iterationCounter.incrementAndGet();
                                return ctx.in().increment();
                            })
                            .until(c -> c.count() >= c.target()
                                    ? LoopStep.<Counter, Integer>done(c.count())
                                    : LoopStep.<Counter, Integer>again(c)))
                    .peek(ctx -> result.set(ctx.in()))
                    .complete());

            return state;
        }).withDatabase(tm).withEventPlatform(TestApplicationClientCreator.createEventPlatform(publisher, outbox, new InMemoryEventConsumer("topic"))));

        final TestClient client = publisherTestClientAndServer.client();
        final TestHttpResponse response = client.post(StubRequest.request("/publishToOther").body("{}"));

        Assert.assertEquals(200, response.statusCode);
        Assert.assertEquals("expected the loop to run once per turn until the target", 3, iterationCounter.get());
        Assert.assertEquals(Integer.valueOf(3), result.get());
        client.assertNoMoreExceptions();
    }

    private record Counter(int count, int target) {
        Counter increment() {
            return new Counter(count + 1, target);
        }
    }
}
