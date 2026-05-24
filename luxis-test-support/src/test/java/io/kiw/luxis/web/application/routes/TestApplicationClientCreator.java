package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.ApplicationRoutesRegister;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.LuxisBuilder;
import io.kiw.luxis.web.test.TestLuxis;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.http.client.LuxisHttpClientConfig;
import io.kiw.luxis.web.test.client.StubLuxisHttpClient;
import io.kiw.luxis.web.http.client.VertxLuxisHttpClient;
import io.kiw.luxis.web.internal.RoutesRegister;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubContextAsserter;
import io.kiw.luxis.web.test.StubNetwork;
import io.kiw.luxis.web.test.StubTestClient;
import io.kiw.luxis.web.test.VertxContextAsserter;
import io.kiw.luxis.web.test.VertxTestClient;
import io.vertx.core.Vertx;
import org.junit.Assume;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TestApplicationClientCreator {

    public static final String STUB_MODE = "stub";
    public static final String REAL_MODE = "real";

    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][] {{STUB_MODE}, {REAL_MODE}});
    }

    public static void assumeRealModeEnabled() {
        Assume.assumeTrue(
                "Skipping real server test: set TEST_MODE=VERTX to enable",
                "VERTX".equals(System.getenv("TEST_MODE")));
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().build(), null, null, null, null, null);
    }

    public static ContextAsserter createContextAsserter(String mode) {
        if (REAL_MODE.equals(mode)) {
            return new VertxContextAsserter();
        } else {
            return new StubContextAsserter();
        }
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, CorsConfig corsConfig) {
        WebServiceConfigBuilder builder = defaultBuilder();
        if (corsConfig != null) {
            builder.setCorsConfig(corsConfig);
        }
        return createTestServerAndClient(mode, registerRoutes, builder.build(), null, null, null, null, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, Consumer<WebServiceConfigBuilder> configCustomizer) {
        return createTestServerAndClient(mode, registerRoutes, configCustomizer, (StubNetwork) null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, Consumer<WebServiceConfigBuilder> configCustomizer, StubNetwork network) {
        WebServiceConfigBuilder builder = defaultBuilder();
        configCustomizer.accept(builder);
        return createTestServerAndClient(mode, registerRoutes, builder.build(), null, null, null, null, network);
    }

    public static LuxisHttpClient createHttpClient(String mode, TestClientAndServer targetServer) {
        return createHttpClient(mode, targetServer, LuxisHttpClientConfig.defaults());
    }

    public static LuxisHttpClient createHttpClient(String mode, TestClientAndServer targetServer, LuxisHttpClientConfig config) {
        if (REAL_MODE.equals(mode)) {
            return new VertxLuxisHttpClient(Vertx.vertx(), config);
        } else {
            return StubLuxisHttpClient.create(targetServer.network(), config);
        }
    }

    public static LuxisHttpClient createHttpClient(String mode, StubNetwork network) {
        return createHttpClient(mode, network, LuxisHttpClientConfig.defaults());
    }

    public static LuxisHttpClient createHttpClient(String mode, StubNetwork network, LuxisHttpClientConfig config) {
        if (REAL_MODE.equals(mode)) {
            return new VertxLuxisHttpClient(Vertx.vertx(), config);
        } else {
            return StubLuxisHttpClient.create(network, config);
        }
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().build(), databaseClient, null, null, null, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().build(), databaseClient, publisher, outboxStore, null, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, int port, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().setPort(port).build(), databaseClient, publisher, outboxStore, null, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore, EventConsumer eventConsumer) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().build(), databaseClient, publisher, outboxStore, eventConsumer, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, int port, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore, EventConsumer eventConsumer) {
        return createTestServerAndClient(mode, registerRoutes, defaultBuilder().setPort(port).build(), databaseClient, publisher, outboxStore, eventConsumer, null);
    }

    private static WebServiceConfigBuilder defaultBuilder() {
        return new WebServiceConfigBuilder().setHost("localhost").setPort(8080);
    }

    private static TestClientAndServer createTestServerAndClient(final String mode, final BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, final WebServerConfig config, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer, final StubNetwork providedNetwork) {
        MyApplicationState state = new MyApplicationState();

        ApplicationRoutesRegister<MyApplicationState> routes = routesRegister -> {
            registerRoutes.accept(routesRegister, state);
            return state;
        };
        final EventPlatform events = toEventPlatform(publisher, outboxStore, eventConsumer);

        final LuxisBuilder<MyApplicationState> luxisBuilder = Luxis.app(routes).withConfig(config).withDatabase(databaseClient).withEventPlatform(events);
        if (REAL_MODE.equals(mode)) {
            final Luxis<MyApplicationState> luxis = luxisBuilder.start();
            return new TestClientAndServer(new VertxTestClient(config.host(), config.port()), luxis, null);
        } else {
            final StubNetwork network = providedNetwork != null ? providedNetwork : new StubNetwork();
            final Luxis<MyApplicationState> luxis = TestLuxis.from(luxisBuilder, network);
            return new TestClientAndServer(new StubTestClient(config.host(), config.port(), network), luxis, network);
        }
    }

    private static EventPlatform toEventPlatform(final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        if (publisher == null && outboxStore == null && eventConsumer == null) {
            return null;
        }
        if (publisher == null || outboxStore == null) {
            throw new IllegalArgumentException(
                    "Mode 3 requires both Publisher and OutboxStore. Use the database-only overload to test scenarios without an event platform.");
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
