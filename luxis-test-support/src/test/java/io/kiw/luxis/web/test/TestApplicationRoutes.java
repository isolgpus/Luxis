package io.kiw.luxis.web.test;

import io.kiw.luxis.web.RouteConfigBuilder;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.RoutesRegister;
import io.kiw.luxis.web.test.handler.BlockingCompleteTestHandler;
import io.kiw.luxis.web.test.handler.BlockingFlatMapFailHandler;
import io.kiw.luxis.web.test.handler.BlockingFlatMapFailWebSocketRoutes;
import io.kiw.luxis.web.test.handler.BlockingMapWebSocketRoutes;
import io.kiw.luxis.web.test.handler.BlockingRequest;
import io.kiw.luxis.web.test.handler.BlockingTestHandler;
import io.kiw.luxis.web.test.handler.EchoRequest;
import io.kiw.luxis.web.test.handler.EchoWebSocketRoutes;
import io.kiw.luxis.web.test.handler.ErrorFilter;
import io.kiw.luxis.web.test.handler.FailingTestHandler;
import io.kiw.luxis.web.test.handler.FileDownloaderHandler;
import io.kiw.luxis.web.test.handler.FileUploaderHandler;
import io.kiw.luxis.web.test.handler.FlatMapFailWebSocketRoutes;
import io.kiw.luxis.web.test.handler.GetEchoHandler;
import io.kiw.luxis.web.test.handler.GetTestFilterHandler;
import io.kiw.luxis.web.test.handler.JwtFilterProtectedHandler;
import io.kiw.luxis.web.test.handler.JwtProtectedHandler;
import io.kiw.luxis.web.test.handler.PostEchoHandler;
import io.kiw.luxis.web.test.handler.StateTestHandler;
import io.kiw.luxis.web.test.handler.StatefulWebSocketRoutes;
import io.kiw.luxis.web.test.handler.StatusCodeRequest;
import io.kiw.luxis.web.test.handler.StatusCodeTestHandler;
import io.kiw.luxis.web.test.handler.TestFilterHandler;
import io.kiw.luxis.web.test.handler.TestFilterRequest;
import io.kiw.luxis.web.test.handler.ThrowRequest;
import io.kiw.luxis.web.test.handler.ThrowTestHandler;
import io.kiw.luxis.web.test.handler.ThrowWebSocketRoutes;
import io.kiw.luxis.web.test.handler.TimeoutTestHandler;
import io.kiw.luxis.web.test.handler.ValidationRequest;
import io.kiw.luxis.web.test.handler.ValidationTestHandler;
import io.kiw.luxis.web.test.jwt.StubJwtProvider;

public final class TestApplicationRoutes {
    private TestApplicationRoutes() {
    }

    public static final String JWT_SECRET = "test-secret-key-for-unit-tests";

    public static MyApplicationState registerRoutes(final RoutesRegister routesRegister, final MyApplicationState state) {
        final StubJwtProvider jwtProvider = new StubJwtProvider(JWT_SECRET);

        routesRegister.jsonFilter("/root/*", state, new TestFilter("rootFilter"));
        routesRegister.jsonFilter("/root/filter/*", state, new TestFilter("pathFilter"));
        routesRegister.jsonFilter("/jwt/filter/*", state, new JwtFilter(jwtProvider));
        routesRegister.jsonFilter("/root/somethingElse/*", state, new TestFilter("otherFilter"));
        routesRegister.jsonRoute("/root/filter/test", Method.POST, state, TestFilterRequest.class, new TestFilterHandler());
        routesRegister.jsonRoute("/root/filter/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        routesRegister.jsonRoute("/root/filter/test", Method.GET, state, Void.class, new GetTestFilterHandler());
        routesRegister.jsonRoute("/root/filter/test", Method.PUT, state, TestFilterRequest.class, new TestFilterHandler());
        routesRegister.jsonRoute("/root/filter/test", Method.DELETE, state, TestFilterRequest.class, new TestFilterHandler());
        routesRegister.jsonRoute("/root/filter/test", Method.PATCH, state, TestFilterRequest.class, new TestFilterHandler());

        routesRegister.jsonRoute("/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler());
        routesRegister.jsonRoute("/echo", Method.PUT, state, EchoRequest.class, new PostEchoHandler());
        routesRegister.jsonRoute("/echo", Method.DELETE, state, EchoRequest.class, new PostEchoHandler());
        routesRegister.jsonRoute("/echo", Method.PATCH, state, EchoRequest.class, new PostEchoHandler());
        routesRegister.jsonRoute("/echo", Method.GET, state, Void.class, new GetEchoHandler());
        routesRegister.jsonRoute("/echo/:pathExample", Method.GET, state, Void.class, new GetEchoHandler());
        routesRegister.jsonRoute("/blocking", Method.POST, state, BlockingRequest.class, new BlockingTestHandler());
        routesRegister.jsonRoute("/failing", Method.POST, state, BlockingRequest.class, new FailingTestHandler());
        routesRegister.jsonRoute("/state", Method.POST, state, Void.class, new StateTestHandler());
        routesRegister.jsonRoute("/throw", Method.POST, state, ThrowRequest.class, new ThrowTestHandler());
        routesRegister.jsonRoute("/blockingComplete", Method.POST, state, BlockingRequest.class, new BlockingCompleteTestHandler());
        routesRegister.jsonRoute("/timing", Method.POST, state, ThrowRequest.class, new TimeoutTestHandler());
        routesRegister.jsonRoute("/validate/:userId", Method.POST, state, ValidationRequest.class, new ValidationTestHandler());
        routesRegister.jsonFilter("/protected/*", state, new ErrorFilter());
        routesRegister.jsonRoute("/protected/resource", Method.GET, state, Void.class, new GetEchoHandler());
        routesRegister.jsonRoute("/blockingFailing", Method.POST, state, BlockingRequest.class, new BlockingFlatMapFailHandler());
        routesRegister.uploadFileRoute("/upload", Method.POST, state, new FileUploaderHandler());
        routesRegister.downloadFileRoute("/download", Method.GET, state, new FileDownloaderHandler(), "text/html; charset=utf-8");
        routesRegister.webSocketRoute("/ws/echo", state, new EchoWebSocketRoutes());
        routesRegister.webSocketRoute("/ws/chat/:room", state, new StatefulWebSocketRoutes());
        routesRegister.webSocketRoute("/ws/blocking", state, new BlockingMapWebSocketRoutes());
        routesRegister.webSocketRoute("/ws/flatMapFail", state, new FlatMapFailWebSocketRoutes());
        routesRegister.webSocketRoute("/ws/blockingFlatMapFail", state, new BlockingFlatMapFailWebSocketRoutes());
        routesRegister.webSocketRoute("/ws/throw", state, new ThrowWebSocketRoutes());
        routesRegister.jsonRoute("/statusCode", Method.POST, state, StatusCodeRequest.class, new StatusCodeTestHandler());
        routesRegister.jsonRoute("/jwt/protected", Method.GET, state, Void.class, new JwtProtectedHandler(jwtProvider));
        routesRegister.jsonRoute("/jwt/filter/test", Method.GET, state, Void.class, new JwtFilterProtectedHandler());

        routesRegister.jsonRoute("/openapi/echo", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                new RouteConfigBuilder()
                        .openApi()
                        .summary("Echo the input")
                        .description("Echoes back the provided values")
                        .tag("echo")
                        .responseDescription("The echoed response")
                        .build()
        );
        routesRegister.jsonRoute("/openapi/echo/:pathExample", Method.GET, state, Void.class, new GetEchoHandler(),
                new RouteConfigBuilder()
                        .openApi()
                        .paramDescription("pathExample", "An example path param")
                        .build()
        );
        routesRegister.jsonRoute("/openapi/hidden", Method.GET, state, Void.class, new GetEchoHandler(),
                new RouteConfigBuilder()
                        .openApi()
                        .hidden()
                        .build()
        );
        routesRegister.jsonRoute("/openapi/timeout", Method.POST, state, EchoRequest.class, new PostEchoHandler(),
                new RouteConfigBuilder()
                        .timeout(5000)
                        .openApi()
                        .summary("Echo with timeout")
                        .done()
                        .build()
        );
        routesRegister.serveOpenApiSpec("/openapi.json", "Test API", "1.0.0", "A test API");

        return state;
    }
}
