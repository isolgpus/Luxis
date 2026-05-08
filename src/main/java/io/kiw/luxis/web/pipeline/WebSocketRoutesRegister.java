package io.kiw.luxis.web.pipeline;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.internal.LuxisPipeline;
import io.kiw.luxis.web.internal.MessagingComponents;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.WebSocketRoute;
import io.kiw.luxis.web.websocket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebSocketRoutesRegister<APP, RESP> {
    private final Map<String, WebSocketRoute<?>> routes;
    private final APP applicationState;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final Map<Class<?>, String> responseTypeRegistry;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final MessagingComponents messaging;

    public WebSocketRoutesRegister(final APP applicationState, final PendingAsyncResponses pendingAsyncResponses, final LinkedHashMap<String, WebSocketRoute<?>> routes, final Map<Class<?>, String> responseTypeRegistry) {
        this(applicationState, pendingAsyncResponses, routes, responseTypeRegistry, null, MessagingComponents.NONE);
    }

    public WebSocketRoutesRegister(final APP applicationState, final PendingAsyncResponses pendingAsyncResponses, final LinkedHashMap<String, WebSocketRoute<?>> routes, final Map<Class<?>, String> responseTypeRegistry, final DatabaseClient<?, ?, ?> databaseClient) {
        this(applicationState, pendingAsyncResponses, routes, responseTypeRegistry, databaseClient, MessagingComponents.NONE);
    }

    public WebSocketRoutesRegister(final APP applicationState, final PendingAsyncResponses pendingAsyncResponses, final LinkedHashMap<String, WebSocketRoute<?>> routes, final Map<Class<?>, String> responseTypeRegistry, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.applicationState = applicationState;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.routes = routes;
        this.responseTypeRegistry = responseTypeRegistry;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    public <T extends RESP> void registerOutbound(final String typeKey, final Class<T> responseClass) {
        if (responseTypeRegistry.containsValue(typeKey)) {
            throw new IllegalArgumentException("Duplicate response type key: " + typeKey);
        }
        responseTypeRegistry.put(responseClass, typeKey);
    }

    public <IN, OUT> void registerInbound(final String typeKey, final Class<IN> messageType, final WebSocketHandler<IN, OUT, APP, RESP, ErrorMessageResponse, WebSocketSession<RESP>> webSocketHandler) {
        if (routes.containsKey(typeKey)) {
            throw new IllegalArgumentException("Duplicate type key: " + typeKey);
        }
        final LuxisStream<IN, APP, RESP, ErrorMessageResponse, WebSocketSession<RESP>> stream = new LuxisStream<>(new ArrayList<>(), applicationState, pendingAsyncResponses, (msg, cause) -> msg, null, databaseClient, messaging);
        final LuxisPipeline<?> pipeline = webSocketHandler.handle(stream);
        routes.put(typeKey, new WebSocketRoute<>(messageType, pipeline));
    }
}
