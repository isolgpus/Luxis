package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.RouteConfig;
import io.kiw.luxis.web.RouteConfigBuilder;
import io.kiw.luxis.web.WebSocketRouteConfig;
import io.kiw.luxis.web.WebSocketRouteConfigBuilder;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.handler.FileDownloadRoute;
import io.kiw.luxis.web.handler.FileUploadRoute;
import io.kiw.luxis.web.handler.JsonFilter;
import io.kiw.luxis.web.handler.JsonHandler;
import io.kiw.luxis.web.handler.WebSocketRoutes;
import io.kiw.luxis.web.http.DownloadFileResponse;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpResult;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.internal.ender.FileEnder;
import io.kiw.luxis.web.internal.ender.JsonEnder;
import io.kiw.luxis.web.messaging.EventSession;
import io.kiw.luxis.web.openapi.OpenApiCollector;
import io.kiw.luxis.web.openapi.OpenApiHandler;
import io.kiw.luxis.web.openapi.RouteDescriptor;
import io.kiw.luxis.web.openapi.TypeResolver;
import io.kiw.luxis.web.pipeline.HttpStream;
import io.kiw.luxis.web.pipeline.LuxisStream;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;

public class RoutesRegister {

    private final RouterWrapper router;
    private final ExecutionDispatcher executionDispatcher;
    private final ObjectMapper objectMapper = JacksonUtil.createMapper();
    private final OpenApiCollector openApiCollector = new OpenApiCollector();
    private final PendingAsyncResponses pendingAsyncResponses;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final MessagingComponents messaging;
    private final LinkedHashMap<String, EventRouteEntry> eventRoutes = new LinkedHashMap<>();

    public RoutesRegister(final RouterWrapper router, final ExecutionDispatcher executionDispatcher, final PendingAsyncResponses pendingAsyncResponses, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.router = router;
        this.executionDispatcher = executionDispatcher;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    public DatabaseClient<?, ?, ?> getDatabaseClient() {
        return databaseClient;
    }


    public <IN, OUT, APP> void jsonRoute(final String path, final Method method, final APP applicationState, final Class<IN> requestType, final JsonHandler<IN, OUT, APP> jsonHandler) {
        jsonRoute(path, method, applicationState, requestType, jsonHandler, new RouteConfigBuilder().build());
    }


    public <IN, OUT, APP> void jsonRoute(final String path, final Method method, final APP applicationState, final Class<IN> requestType, final JsonHandler<IN, OUT, APP> jsonHandler, final RouteConfig routeConfig) {

        final ArrayList<MapInstruction> chain = new ArrayList<>();
        new HttpStream<>(chain, applicationState, pendingAsyncResponses, new JsonEnder(objectMapper), databaseClient, messaging)
                .flatMap(ctx -> {
                    ctx.session().addResponseHeader("Content-Type", "application/json");

                    if (method.canHaveABody() && ctx.session().ctx.getRequestBody() == null && !requestType.equals(Void.class)) {
                        return HttpResult.error(ErrorStatusCode.BAD_REQUEST, new ErrorMessageResponse("Invalid json request"));
                    }

                    try {
                        final IN jsonRequest = method.canHaveABody() && !requestType.equals(Void.class) ? objectMapper.readValue(ctx.session().ctx.getRequestBody(), requestType) : null;
                        return HttpResult.success(jsonRequest);
                    } catch (final Exception e) {
                        return HttpResult.error(ErrorStatusCode.BAD_REQUEST, new ErrorMessageResponse("Invalid json request"));
                    }
                }).flatMap(ctx -> HttpResult.success(ctx.in()));

        final HttpStream<IN, APP> httpStream = new HttpStream<>(chain, applicationState, pendingAsyncResponses, new JsonEnder(objectMapper), databaseClient, messaging);
        final LuxisPipeline<?> flow = jsonHandler.handle(httpStream);

        final Type[] typeArgs = TypeResolver.resolveTypeArguments(jsonHandler.getClass(), JsonHandler.class);
        openApiCollector.addRoute(new RouteDescriptor(
                path, method,
                requestType,
                typeArgs != null ? typeArgs[1] : null,
                "application/json", "application/json",
                RouteDescriptor.RouteKind.JSON,
                routeConfig.openApiMetadata().orElse(null)
        ));

        router.route(path, method, "*", "application/json", flow, routeConfig);
    }

    public <APP> void jsonFilter(final String path, final APP applicationState, final JsonFilter<APP> jsonFilter) {
        jsonFilter(path, applicationState, jsonFilter, new RouteConfigBuilder().build());
    }

    public <APP> void jsonFilter(final String path, final APP applicationState, final JsonFilter<APP> jsonFilter, final RouteConfig routeConfig) {
        final ArrayList<MapInstruction> chain = new ArrayList<>();
        new HttpStream<Void, APP>(chain, applicationState, pendingAsyncResponses, null, databaseClient, messaging)
                .map(ctx -> {
                    ctx.session().addResponseHeader("Content-Type", "application/json");
                    return null;
                });
        final HttpStream<Void, APP> httpStream = new HttpStream<>(chain, applicationState, pendingAsyncResponses, null, databaseClient, messaging);
        final LuxisPipeline<Void> flow = jsonFilter.handle(httpStream);


        router.route(path, "*", "application/json", flow, routeConfig);
    }

    public <OUT, APP> void uploadFileRoute(final String path, final Method method, final APP applicationState, final FileUploadRoute<OUT, APP> fileUploaderHandler) {

        final HttpStream<Map<String, HttpBuffer>, APP> httpStream = new HttpStream<>(new ArrayList<>(), applicationState, pendingAsyncResponses, new JsonEnder(objectMapper), databaseClient, messaging);

        httpStream.flatMap(ctx -> {
            ctx.session().addResponseHeader("Content-Type", "application/json");

            final Map<String, HttpBuffer> uploadedFile = ctx.session().resolveUploadedFiles();
            return HttpResult.success(uploadedFile);
        });
        final LuxisPipeline<OUT> flow = fileUploaderHandler.handle(httpStream);

        final Type[] typeArgs = TypeResolver.resolveTypeArguments(fileUploaderHandler.getClass(), FileUploadRoute.class);
        openApiCollector.addRoute(new RouteDescriptor(
                path, method,
                null,
                typeArgs != null ? typeArgs[0] : null,
                "multipart/form-data", "application/json",
                RouteDescriptor.RouteKind.UPLOAD,
                null
        ));

        router.route(path, method, "multipart/form-data", "application/json", flow, new RouteConfigBuilder().build());
    }

    public <APP, T> void eventRoute(final String key, final APP applicationState, final Class<T> messageType,
                                    final Function<LuxisStream<T, APP, Void, ErrorMessageResponse, EventSession>, LuxisPipeline<?>> handler) {
        if (eventRoutes.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate event route key: " + key);
        }
        final LuxisStream<T, APP, Void, ErrorMessageResponse, EventSession> stream =
                new LuxisStream<>(new ArrayList<>(), applicationState, pendingAsyncResponses, (msg, cause) -> msg, null, databaseClient, messaging);
        final LuxisPipeline<?> pipeline = handler.apply(stream);
        eventRoutes.put(key, new EventRouteEntry(pipeline, messageType));
    }

    public Map<String, EventRouteEntry> getEventRoutes() {
        return eventRoutes;
    }

    public record EventRouteEntry(LuxisPipeline<?> pipeline, Class<?> messageType) {
    }

    public ExecutionDispatcher getExecutionDispatcher() {
        return executionDispatcher;
    }

    public PendingAsyncResponses getPendingAsyncResponses() {
        return pendingAsyncResponses;
    }

    public MessagingComponents getMessaging() {
        return messaging;
    }

    public <APP, RESP> void webSocketRoute(final String path, final APP applicationState, final WebSocketRoutes<APP, RESP> route) {
        webSocketRoute(path, applicationState, route, new WebSocketRouteConfigBuilder().build());
    }

    public <APP, RESP> void webSocketRoute(final String path, final APP applicationState, final WebSocketRoutes<APP, RESP> route, final WebSocketRouteConfig config) {
        final HttpWebSocketRouteHandlerImpl<APP, RESP> handler = new HttpWebSocketRouteHandlerImpl<>(route, objectMapper, applicationState, router.getExceptionHandler(), executionDispatcher, config, pendingAsyncResponses, databaseClient, messaging);
        router.webSocketRoute(path, handler);
    }

    public <IN, APP> void downloadFileRoute(final String path, final Method method, final APP applicationState, final FileDownloadRoute<IN, APP> fileDownloadHandler, final String contentType) {
        final HttpStream<IN, APP> httpStream = new HttpStream<>(new ArrayList<>(), applicationState, pendingAsyncResponses, new FileEnder(), databaseClient, messaging);

        httpStream.flatMap(ctx -> {
            ctx.session().addResponseHeader("Content-Type", contentType);
            ctx.session().addResponseHeader(TRANSFER_ENCODING, "chunked");
            return HttpResult.success(ctx.in());
        });
        final LuxisPipeline<?> flow = fileDownloadHandler.handle(httpStream);

        final Type[] typeArgs = TypeResolver.resolveTypeArguments(fileDownloadHandler.getClass(), FileDownloadRoute.class);
        openApiCollector.addRoute(new RouteDescriptor(
                path, method,
                typeArgs != null ? typeArgs[0] : null,
                DownloadFileResponse.class,
                "*", contentType,
                RouteDescriptor.RouteKind.DOWNLOAD,
                null
        ));

        router.route(path, method, "*", contentType, flow, new RouteConfigBuilder().build());
    }

    public void serveOpenApiSpec(final String path, final String title, final String version, final String description) {
        final OpenApiHandler openApiRoute = new OpenApiHandler(openApiCollector, objectMapper, title, version, description);
        final RouteConfig config = new RouteConfigBuilder().openApi().hidden().build();
        jsonRoute(path, Method.GET, null, Void.class, openApiRoute, config);
    }
}
