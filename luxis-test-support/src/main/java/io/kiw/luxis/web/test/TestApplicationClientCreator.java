package io.kiw.luxis.web.test;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.LuxisBuilder;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.http.client.LuxisHttpClientConfig;
import io.kiw.luxis.web.test.client.StubLuxisHttpClient;
import io.kiw.luxis.web.http.client.VertxLuxisHttpClient;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.vertx.core.Vertx;

import java.util.Arrays;
import java.util.Collection;

public class TestApplicationClientCreator {

    private final StubNetwork network = new StubNetwork();

    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][] {{TestMode.STUB}, {TestMode.REAL}});
    }

    public static ContextAsserter createContextAsserter(final TestMode mode) {
        if (mode == TestMode.REAL) {
            return new VertxContextAsserter();
        } else {
            return new StubContextAsserter();
        }
    }

    public LuxisHttpClient createHttpClient(final TestMode mode) {
        return createHttpClient(mode, LuxisHttpClientConfig.defaults());
    }

    public LuxisHttpClient createHttpClient(final TestMode mode, final LuxisHttpClientConfig config) {
        if (mode == TestMode.REAL) {
            return new VertxLuxisHttpClient(Vertx.vertx(), config);
        } else {
            return StubLuxisHttpClient.create(network, config);
        }
    }

    public <T> TestClientAndServer createTestServerAndClient(final TestMode mode, final LuxisBuilder<T> builder) {
        final WebServerConfig config = builder.getConfig();
        if (mode == TestMode.REAL) {
            final Luxis<T> luxis = builder.start(Vertx.vertx());
            return new TestClientAndServer(new VertxTestClient("127.0.0.1", config.port()), luxis);
        }
        final Luxis<T> luxis = TestLuxis.from(builder, network);
        return new TestClientAndServer(new StubTestClient(config.host(), config.port(), network), luxis);
    }

    public static EventPlatform createEventPlatform(final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        if (publisher == null && outboxStore == null && eventConsumer == null) {
            return null;
        }
        if (publisher == null || outboxStore == null) {
            throw new IllegalArgumentException(
                    "An event platform requires both Publisher and OutboxStore.");
        }
        return EventPlatform.of(publisher, outboxStore, eventConsumer != null ? eventConsumer : NoopEventConsumer.INSTANCE);
    }

    private static final class NoopEventConsumer implements EventConsumer {
        static final NoopEventConsumer INSTANCE = new NoopEventConsumer();

        @Override
        public String topic() {
            return "__noop__";
        }

        @Override
        public String extractKey(final java.nio.ByteBuffer message) {
            return null;
        }

        @Override
        public <T> T decode(final java.nio.ByteBuffer message, final Class<T> type) {
            return null;
        }

        @Override
        public void start(final io.kiw.luxis.web.messaging.EventDispatcher dispatcher) {
        }

        @Override
        public void close() {
        }
    }
}
