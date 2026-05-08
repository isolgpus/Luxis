package io.kiw.luxis.web.application.routes;

import io.kiw.luxis.web.ApplicationRoutesRegister;
import io.kiw.luxis.web.Luxis;
import io.kiw.luxis.web.TestLuxis;
import io.kiw.luxis.web.WebServerConfig;
import io.kiw.luxis.web.WebServiceConfigBuilder;
import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.client.LuxisHttpClient;
import io.kiw.luxis.web.http.client.LuxisHttpClientConfig;
import io.kiw.luxis.web.http.client.StubLuxisHttpClient;
import io.kiw.luxis.web.http.client.VertxLuxisHttpClient;
import io.kiw.luxis.web.internal.RoutesRegister;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventPlatform;
import io.kiw.luxis.web.messaging.OutboxStore;
import io.kiw.luxis.web.messaging.Publisher;
import io.kiw.luxis.web.test.ContextAsserter;
import io.kiw.luxis.web.test.MyApplicationState;
import io.kiw.luxis.web.test.StubContextAsserter;
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
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(8080);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build());
    }

    public static ContextAsserter createContextAsserter(String mode) {
        if (REAL_MODE.equals(mode)) {
            return new VertxContextAsserter();
        } else {
            return new StubContextAsserter();
        }
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, CorsConfig corsConfig) {
        WebServiceConfigBuilder builder = new WebServiceConfigBuilder().setPort(8080);
        if (corsConfig != null) {
            builder.setCorsConfig(corsConfig);
        }
        return createTestServerAndClient(mode, registerRoutes, builder.build());
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, Consumer<WebServiceConfigBuilder> configCustomizer) {
        WebServiceConfigBuilder builder = new WebServiceConfigBuilder().setPort(8080);
        configCustomizer.accept(builder);
        return createTestServerAndClient(mode, registerRoutes, builder.build());
    }

    public static LuxisHttpClient createHttpClient(String mode, TestClientAndServer targetServer) {
        if (REAL_MODE.equals(mode)) {
            return new VertxLuxisHttpClient(Vertx.vertx());
        } else {
            return StubLuxisHttpClient.create((TestLuxis<?>) targetServer.luxis());
        }
    }

    public static LuxisHttpClient createHttpClient(String mode, TestClientAndServer targetServer, LuxisHttpClientConfig config) {
        if (REAL_MODE.equals(mode)) {
            return new VertxLuxisHttpClient(Vertx.vertx(), config);
        } else {
            return StubLuxisHttpClient.create((TestLuxis<?>) targetServer.luxis(), config);
        }
    }

    private static TestClientAndServer createTestServerAndClient(final String mode, final BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, final WebServerConfig config) {
        return createTestServerAndClient(mode, registerRoutes, config, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient) {
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(8080);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build(), databaseClient);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore) {
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(8080);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build(), databaseClient, publisher, outboxStore, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, int port, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore) {
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(port);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build(), databaseClient, publisher, outboxStore, null);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore, EventConsumer eventConsumer) {
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(8080);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build(), databaseClient, publisher, outboxStore, eventConsumer);
    }

    public static TestClientAndServer createTestServerAndClient(String mode, BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, int port, DatabaseClient<?, ?, ?> databaseClient, Publisher publisher, OutboxStore<?> outboxStore, EventConsumer eventConsumer) {
        final WebServiceConfigBuilder webServiceConfigBuilder = new WebServiceConfigBuilder().setPort(port);
        return createTestServerAndClient(mode, registerRoutes, webServiceConfigBuilder.build(), databaseClient, publisher, outboxStore, eventConsumer);
    }

    private static TestClientAndServer createTestServerAndClient(final String mode, final BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, final WebServerConfig config, final DatabaseClient<?, ?, ?> databaseClient) {
        return createTestServerAndClient(mode, registerRoutes, config, databaseClient, null, null, null);
    }

    private static TestClientAndServer createTestServerAndClient(final String mode, final BiConsumer<RoutesRegister, MyApplicationState> registerRoutes, final WebServerConfig config, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        MyApplicationState state = new MyApplicationState();

        ApplicationRoutesRegister<MyApplicationState> routes = routesRegister -> {
            registerRoutes.accept(routesRegister, state);
            return state;
        };

        if (REAL_MODE.equals(mode)) {
            Luxis<MyApplicationState> luxis = startReal(routes, config, databaseClient, publisher, outboxStore, eventConsumer);
            return new TestClientAndServer(new VertxTestClient("127.0.0.1", config.port()), luxis);
        } else {
            Luxis<MyApplicationState> luxis = startTest(routes, config, databaseClient, publisher, outboxStore, eventConsumer);
            return new TestClientAndServer(new StubTestClient("127.0.0.1", config.port(), luxis), luxis);
        }
    }

    private static Luxis<MyApplicationState> startReal(final ApplicationRoutesRegister<MyApplicationState> routes, final WebServerConfig config, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        final EventPlatform events = toEventPlatform(publisher, outboxStore, eventConsumer);
        if (events != null) {
            return Luxis.start(routes, config, databaseClient, events);
        }
        if (databaseClient != null) {
            return Luxis.start(routes, config, databaseClient);
        }
        return Luxis.start(routes, config);
    }

    private static Luxis<MyApplicationState> startTest(final ApplicationRoutesRegister<MyApplicationState> routes, final WebServerConfig config, final DatabaseClient<?, ?, ?> databaseClient, final Publisher publisher, final OutboxStore<?> outboxStore, final EventConsumer eventConsumer) {
        final EventPlatform events = toEventPlatform(publisher, outboxStore, eventConsumer);
        if (events != null) {
            return Luxis.test(routes, config, databaseClient, events);
        }
        if (databaseClient != null) {
            return Luxis.test(routes, config, databaseClient);
        }
        return Luxis.test(routes, config);
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
