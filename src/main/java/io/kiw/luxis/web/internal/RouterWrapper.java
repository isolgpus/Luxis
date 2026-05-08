package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.RouteConfig;
import io.kiw.luxis.web.cors.CorsConfig;
import io.kiw.luxis.web.db.DatabaseClient;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpSession;
import io.kiw.luxis.web.http.HttpSuccessResponse;
import io.kiw.luxis.web.http.Method;
import io.kiw.luxis.web.http.RequestContext;
import io.kiw.luxis.web.internal.ender.Ender;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class RouterWrapper {

    private final Consumer<Exception> exceptionHandler;
    private final PendingAsyncResponses pendingAsyncResponses;
    private final TransactionExecutor transactionExecutor;
    private final DatabaseClient<?, ?, ?> databaseClient;
    private final MessagingComponents messaging;

    public RouterWrapper(final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor) {
        this(exceptionHandler, pendingAsyncResponses, transactionExecutor, null, MessagingComponents.NONE);
    }

    public RouterWrapper(final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor, final DatabaseClient<?, ?, ?> databaseClient) {
        this(exceptionHandler, pendingAsyncResponses, transactionExecutor, databaseClient, MessagingComponents.NONE);
    }

    public RouterWrapper(final Consumer<Exception> exceptionHandler, final PendingAsyncResponses pendingAsyncResponses, final TransactionExecutor transactionExecutor, final DatabaseClient<?, ?, ?> databaseClient, final MessagingComponents messaging) {
        this.exceptionHandler = exceptionHandler;
        this.pendingAsyncResponses = pendingAsyncResponses;
        this.transactionExecutor = transactionExecutor;
        this.databaseClient = databaseClient;
        this.messaging = messaging != null ? messaging : MessagingComponents.NONE;
    }

    Consumer<Exception> getExceptionHandler() {
        return exceptionHandler;
    }

    private final ObjectMapper objectMapper = JacksonUtil.createMapper();

    protected abstract void route(final String path, final Method method, final String consumes, final String provides, final LuxisPipeline<?> flow, final RouteConfig routeConfig);

    protected abstract void route(final String path, final String consumes, final String provides, final LuxisPipeline<?> flow, final RouteConfig routeConfig);

    public abstract void configureCors(final CorsConfig corsConfig);

    protected abstract void webSocketRoute(final String path, final HttpWebSocketRouteHandler handler);

    public <T> void handle(final MapInstruction<Object, T, Object, HttpSession, HttpErrorResponse> applicationInstruction, final RequestContext vertxContext, final Object applicationState, final Ender ender) {
        final HttpSession httpSession = new HttpSession(vertxContext);
        final Result<HttpErrorResponse, T> result;
        try {
            result = applicationInstruction.handle(vertxContext.get("state"), httpSession, applicationState);
        } catch (final Exception e) {
            handleException(vertxContext, e);
            return;
        }

        processResult(result, applicationInstruction, vertxContext, ender);
    }

    protected <T> CompletableFuture<Result<HttpErrorResponse, T>> handleAsync(final MapInstruction<Object, T, Object, HttpSession, HttpErrorResponse> applicationInstruction, final RequestContext requestContext, final Object applicationState, final Ender ender) {
        final HttpSession httpSession = new HttpSession(requestContext);
        final CompletableFuture<Result<HttpErrorResponse, T>> future;
        try {
            future = applicationInstruction.handleAsync(requestContext.get("state"), httpSession, applicationState, pendingAsyncResponses, databaseClient, messaging);
        } catch (final Exception e) {
            handleException(requestContext, e);
            return null;
        }


        future.whenComplete((result, throwable) ->
                requestContext.runOnContext(() -> {
                    if (throwable != null) {
                        handleException(requestContext, throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                        return;
                    }
                    processResult(result, applicationInstruction, requestContext, ender);
                })
        );

        return future;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void handleTransactional(final MapInstruction applicationInstruction, final RequestContext requestContext, final Object applicationState, final Ender ender) {
        if (transactionExecutor == null) {
            handleException(requestContext, new IllegalStateException(
                    "Encountered transactional instruction but no DatabaseClient is registered."));
            return;
        }
        final HttpSession httpSession = new HttpSession(requestContext);
        final Consumer<Exception> wrappedExceptionHandler = e -> handleException(requestContext, e);
        transactionExecutor.execute(httpSession, applicationState, applicationInstruction, requestContext.get("state"), wrappedExceptionHandler, new TransactionExecutor.Callbacks() {
            @Override
            public void onSuccess(final Object finalValue) {
                processResult(Result.success(finalValue), applicationInstruction, requestContext, ender);
            }

            @Override
            public void onSubChainError(final Object errValue) {
                processResult(Result.error((HttpErrorResponse) errValue), applicationInstruction, requestContext, ender);
            }
        });
    }

    @SuppressWarnings("IllegalCatch")
    private <T> void processResult(final Result<HttpErrorResponse, T> result, final MapInstruction<?, T, ?, HttpSession, HttpErrorResponse> applicationInstruction, final RequestContext requestContext, final Ender ender) {
        result.consume(httpErrorResponse -> {
                    requestContext.setStatusCode(httpErrorResponse.statusCode());
                    requestContext.end(this.objectMapper.writeValueAsString(httpErrorResponse.errorMessageValue()));
                },
                s -> {
                    if (applicationInstruction.lastStep) {
                        try {
                            Object value = s;
                            if (s instanceof HttpSuccessResponse<?> successResponse) {
                                requestContext.setStatusCode(successResponse.statusCode());
                                value = successResponse.value();
                            }
                            ender.end(requestContext, value);
                        } catch (final RuntimeException e) {
                            handleException(requestContext, e);
                        }
                    } else {
                        requestContext.put("state", s);
                        requestContext.next();
                    }
                });
    }

    private void handleException(final RequestContext requestContext, final Exception e) {
        exceptionHandler.accept(e);
        requestContext.setStatusCode(500);
        requestContext.end("{\"message\":\"Something went wrong\"}");
    }
}
