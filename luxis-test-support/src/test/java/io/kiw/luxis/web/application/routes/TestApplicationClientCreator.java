package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.LuxisBuilder;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.test.TestLuxis;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.http.client.LuxisHttpClientConfig;
import io.kiw.luxis.web.test.client.StubLuxisHttpClient;
import io.kiw.luxis.web.http.client.VertxLuxisHttpClient;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.StubContextAsserter;
import io.kiw.luxis.web.test.StubNetwork;
import io.kiw.luxis.web.test.StubTestClient;
import io.kiw.luxis.web.test.VertxContextAsserter;
import io.kiw.luxis.web.test.VertxTestClient;
import io.vertx.core.Vertx;
import org.junit.Assume;

import java.util.Arrays;
import java.util.Collection;

public class TestApplicationClientCreator {

    public static final String STUB_MODE = "stub";
    public static final String REAL_MODE = "real";

    private final StubNetwork network = new StubNetwork();

    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][] {{STUB_MODE}, {REAL_MODE}});
    }

    public static void assumeRealModeEnabled() {
        Assume.assumeTrue(
                "Skipping real server test: set TEST_MODE=VERTX to enable",
                "VERTX".equals(System.getenv("TEST_MODE")));
    }

    public static ContextAsserter createContextAsserter(String mode) {
        if (REAL_MODE.equals(mode)) {
            return new VertxContextAsserter();
        } else {
            return new StubContextAsserter();
        }
    }

    public StubNetwork network() {
        return network;
    }

    public LuxisHttpClient createHttpClient(String mode) {
        return createHttpClient(mode, LuxisHttpClientConfig.defaults());
    }

    public LuxisHttpClient createHttpClient(String mode, LuxisHttpClientConfig config) {
        if (REAL_MODE.equals(mode)) {
            return new VertxLuxisHttpClient(Vertx.vertx(), config);
        } else {
            return StubLuxisHttpClient.create(network, config);
        }
    }

    public <T> TestClientAndServer createTestServerAndClient(final String mode, final LuxisBuilder<T> builder) {
        final WebServerConfig config = builder.getConfig();
        if (REAL_MODE.equals(mode)) {
            final Luxis<T> luxis = builder.start();
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
