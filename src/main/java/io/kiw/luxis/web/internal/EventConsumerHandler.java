package io.kiw.luxis.web.internal;

import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.internal.RoutesRegister.EventRouteEntry;
import io.kiw.luxis.web.messaging.EventConsumer;
import io.kiw.luxis.web.messaging.EventSession;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class EventConsumerHandler {

    private final EventConsumer eventConsumer;
    private final Map<String, EventRouteEntry> routes;
    private final Map<String, LuxisPipelineExecutor<EventSession>> executors;
    private final Consumer<Exception> exceptionHandler;

    public EventConsumerHandler(
            final EventConsumer eventConsumer,
            final Map<String, EventRouteEntry> routes,
            final Consumer<Exception> exceptionHandler,
            final ExecutionDispatcher executionDispatcher,
            final PendingAsyncResponses pendingAsyncResponses,
            final DatabaseClient<?, ?, ?> databaseClient,
            final MessagingComponents messaging) {
        this.eventConsumer = eventConsumer;
        this.routes = routes;
        this.exceptionHandler = exceptionHandler;
        this.executors = new HashMap<>();
        final LuxisPipelineHandler<EventSession> handler = new LuxisPipelineHandler<>() {
            @Override
            public void handleFailure(final EventSession session, final MapInstruction<?, ?, ?, ?, ?> instruction, final ErrorMessageResponse error) {
                exceptionHandler.accept(new RuntimeException("Event pipeline failure: " + error.message()));
            }

            @Override
            public void sendFinalResponse(final EventSession session, final Object result) {
                // events have no reply
            }
        };
        for (final Map.Entry<String, EventRouteEntry> entry : routes.entrySet()) {
            final Object appState = entry.getValue().pipeline().getApplicationState();
            executors.put(entry.getKey(), new LuxisPipelineExecutor<>(
                    appState, exceptionHandler, executionDispatcher, pendingAsyncResponses, handler, databaseClient, messaging));
        }
    }

    public void start() {
        eventConsumer.start(this::dispatch);
    }

    public void close() {
        try {
            eventConsumer.close();
        } catch (final Exception e) {
            exceptionHandler.accept(e);
        }
    }

    private void dispatch(final ByteBuffer message) {
        final String key;
        try {
            key = eventConsumer.extractKey(message);
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            return;
        }
        final EventRouteEntry entry = routes.get(key);
        final LuxisPipelineExecutor<EventSession> executor = executors.get(key);
        if (entry == null || executor == null) {
            exceptionHandler.accept(new IllegalStateException("No event route registered for key: " + key));
            return;
        }
        final Object payload;
        try {
            payload = eventConsumer.decode(message, entry.messageType());
        } catch (final Exception e) {
            exceptionHandler.accept(e);
            return;
        }
        try {
            executor.execute(new EventSession(), entry.pipeline(), payload);
        } catch (final Exception e) {
            exceptionHandler.accept(e);
        }
    }
}
