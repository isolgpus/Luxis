package io.kiw.luxis.web.http.client;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.internal.ClientWebSocketHandler;
import io.kiw.luxis.web.internal.PendingAsyncResponses;
import io.kiw.luxis.web.internal.VertxClientWebSocketConnection;
import io.kiw.luxis.web.internal.VertxExecutionDispatcher;
import io.kiw.luxis.web.internal.VertxTimeoutScheduler;
import io.kiw.luxis.web.websocket.ClientWebSocketRoutes;
import io.kiw.luxis.web.websocket.WebSocketSession;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class VertxLuxisHttpClient implements LuxisHttpClient {

    private final Vertx vertx;
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;
    private final LuxisHttpClientConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public VertxLuxisHttpClient(final Vertx vertx) {
        this(vertx, LuxisHttpClientConfig.defaults());
    }

    public VertxLuxisHttpClient(final Vertx vertx, final LuxisHttpClientConfig config) {
        this(vertx, config, new HttpClientOptions());
    }

    public VertxLuxisHttpClient(final Vertx vertx, final LuxisHttpClientConfig config, final HttpClientOptions httpClientOptions) {
        this.vertx = vertx;
        this.config = config;
        this.httpClient = vertx.createHttpClient(httpClientOptions);
        this.webSocketClient = vertx.createWebSocketClient();
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> get(final HttpClientRequest request, final Class<T> responseType) {
        return send(request, HttpMethod.GET, responseType);
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> post(final HttpClientRequest request, final Class<T> responseType) {
        return send(request, HttpMethod.POST, responseType);
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> put(final HttpClientRequest request, final Class<T> responseType) {
        return send(request, HttpMethod.PUT, responseType);
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> delete(final HttpClientRequest request, final Class<T> responseType) {
        return send(request, HttpMethod.DELETE, responseType);
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> patch(final HttpClientRequest request, final Class<T> responseType) {
        return send(request, HttpMethod.PATCH, responseType);
    }

    @Override
    public <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> postFiles(final HttpClientRequest request, final Class<T> responseType) {
        final CompletableFuture<Result<HttpErrorResponse, HttpClientResponse<T>>> future = new CompletableFuture<>();
        final String resolvedUrl = resolveUrl(request.getUrl());
        final URI uri = URI.create(resolvedUrl);

        final String host = uri.getHost();
        final int port = uri.getPort() != -1 ? uri.getPort() : (
                "https".equals(uri.getScheme()) ? 443 : 80
        );
        final String requestUri = buildRequestUri(uri, request.getQueryParams());

        httpClient.request(HttpMethod.POST, port, host, requestUri)
                .onSuccess(req -> {
                    try {
                        for (final Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                            req.putHeader(header.getKey(), header.getValue());
                        }

                        final String boundary = "----LuxisHttpClientBoundary" + System.nanoTime();
                        req.putHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

                        final Buffer multipartBody = Buffer.buffer();
                        for (final Map.Entry<String, HttpBuffer> upload : request.getFileUploads().entrySet()) {
                            multipartBody.appendString("--" + boundary + "\r\n");
                            multipartBody.appendString("Content-Disposition: form-data; name=\"" + upload.getKey() + "\"; filename=\"" + upload.getKey() + "\"\r\n");
                            multipartBody.appendString("Content-Type: application/octet-stream\r\n\r\n");
                            multipartBody.appendBuffer(Buffer.buffer(upload.getValue().bytes()));
                            multipartBody.appendString("\r\n");
                        }
                        multipartBody.appendString("--" + boundary + "--\r\n");

                        req.send(multipartBody)
                                .onSuccess(resp -> handleResponse(resp, future, responseType))
                                .onFailure(future::completeExceptionally);
                    } catch (final Exception t) {
                        future.completeExceptionally(t);
                    }
                })
                .onFailure(future::completeExceptionally);

        return new LuxisAsync<>(future);
    }

    @Override
    public LuxisAsync<HttpClientResponse<HttpBuffer>, HttpErrorResponse> download(final HttpClientRequest request) {
        final CompletableFuture<Result<HttpErrorResponse, HttpClientResponse<HttpBuffer>>> future = new CompletableFuture<>();
        final String resolvedUrl = resolveUrl(request.getUrl());
        final URI uri = URI.create(resolvedUrl);

        final String host = uri.getHost();
        final int port = uri.getPort() != -1 ? uri.getPort() : (
                "https".equals(uri.getScheme()) ? 443 : 80
        );
        final String requestUri = buildRequestUri(uri, request.getQueryParams());

        httpClient.request(HttpMethod.GET, port, host, requestUri)
                .onSuccess(req -> {
                    try {
                        for (final Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                            req.putHeader(header.getKey(), header.getValue());
                        }

                        req.send()
                                .onSuccess(resp -> handleDownloadResponse(resp, future))
                                .onFailure(future::completeExceptionally);
                    } catch (final Exception t) {
                        future.completeExceptionally(t);
                    }
                })
                .onFailure(future::completeExceptionally);

        return new LuxisAsync<>(future);
    }

    private void handleDownloadResponse(final io.vertx.core.http.HttpClientResponse resp,
                                        final CompletableFuture<Result<HttpErrorResponse, HttpClientResponse<HttpBuffer>>> future) {
        resp.body()
                .onSuccess(body -> {
                    try {
                        final Map<String, String> headers = new LinkedHashMap<>();
                        for (final String name : resp.headers().names()) {
                            headers.put(name, resp.getHeader(name));
                        }

                        if (config.isErrorAwareResponses() && resp.statusCode() >= 400) {
                            future.complete(Result.error(toHttpErrorResponse(body.toString(), resp.statusCode())));
                        } else {
                            future.complete(Result.success(new HttpClientResponse<>(resp.statusCode(), new HttpBuffer(body.getBytes()), headers)));
                        }
                    } catch (final Exception t) {
                        future.completeExceptionally(t);
                    }
                })
                .onFailure(future::completeExceptionally);
    }

    private <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> send(final HttpClientRequest request, final HttpMethod method, final Class<T> responseType) {
        final CompletableFuture<Result<HttpErrorResponse, HttpClientResponse<T>>> future = new CompletableFuture<>();
        final String resolvedUrl = resolveUrl(request.getUrl());
        final URI uri = URI.create(resolvedUrl);

        final String host = uri.getHost();
        final int port = uri.getPort() != -1 ? uri.getPort() : (
                "https".equals(uri.getScheme()) ? 443 : 80
        );
        final String requestUri = buildRequestUri(uri, request.getQueryParams());

        httpClient.request(method, port, host, requestUri)
                .onSuccess(req -> {
                    try {
                        for (final Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                            req.putHeader(header.getKey(), header.getValue());
                        }

                        if (request.getBody() != null) {
                            req.putHeader("Content-Type", "application/json");

                            req.send(Buffer.buffer(request.getBody() instanceof String ? (String) request.getBody() : mapper.writeValueAsString(request.getBody())))
                                    .onSuccess(resp -> handleResponse(resp, future, responseType))
                                    .onFailure(future::completeExceptionally);
                        } else {
                            req.send()
                                    .onSuccess(resp -> handleResponse(resp, future, responseType))
                                    .onFailure(future::completeExceptionally);
                        }
                    } catch (final Exception t) {
                        future.completeExceptionally(t);
                    }
                })
                .onFailure(future::completeExceptionally);

        return new LuxisAsync<>(future);
    }

    @Override
    public <APP, RESP> WebSocketSession<RESP> connectToWebSocket(final String path, final ClientWebSocketRoutes<APP, RESP> routes) {
        final String resolvedUrl = resolveUrl(path);
        final URI uri = URI.create(resolvedUrl);

        final String host = uri.getHost();
        final int port = uri.getPort() != -1 ? uri.getPort() : (
                "https".equals(uri.getScheme()) ? 443 : 80
        );
        final String requestUri = uri.getRawPath() != null ? uri.getRawPath() : "/";

        final VertxExecutionDispatcher executionDispatcher = new VertxExecutionDispatcher(vertx);
        final VertxTimeoutScheduler timeoutScheduler = new VertxTimeoutScheduler(vertx);
        final PendingAsyncResponses pendingAsyncResponses = new PendingAsyncResponses(timeoutScheduler, e -> {
            throw new RuntimeException(e);
        });
        final ClientWebSocketHandler<APP, RESP> handler = new ClientWebSocketHandler<>(routes, mapper, executionDispatcher, pendingAsyncResponses, e -> {
            throw new RuntimeException(e);
        });

        final WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setHost(host)
                .setPort(port)
                .setURI(requestUri);

        final CompletableFuture<WebSocketSession<RESP>> future = new CompletableFuture<>();

        webSocketClient.connect(options)
                .onSuccess(ws -> {
                    final VertxClientWebSocketConnection connection = new VertxClientWebSocketConnection(ws);
                    final WebSocketSession<RESP> session = handler.createSession(connection);
                    ws.textMessageHandler(msg -> handler.onMessage(msg, session));
                    future.complete(session);
                })
                .onFailure(future::completeExceptionally);

        return future.join();
    }

    private String resolveUrl(final String url) {
        if (config.getBaseUrl() == null || url.contains("://")) {
            return url;
        }
        final String base = config.getBaseUrl().endsWith("/")
                ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1)
                : config.getBaseUrl();
        final String path = url.startsWith("/") ? url : "/" + url;
        return base + path;
    }

    @SuppressWarnings("unchecked")
    private <T> void handleResponse(final io.vertx.core.http.HttpClientResponse resp,
                                    final CompletableFuture<Result<HttpErrorResponse, HttpClientResponse<T>>> future,
                                    final Class<T> responseType) {
        resp.body()
                .onSuccess(body -> {
                    try {
                        final String rawBody = body.toString();
                        final Map<String, String> headers = new LinkedHashMap<>();
                        for (final String name : resp.headers().names()) {
                            headers.put(name, resp.getHeader(name));
                        }

                        if (config.isErrorAwareResponses() && resp.statusCode() >= 400) {
                            future.complete(Result.error(toHttpErrorResponse(rawBody, resp.statusCode())));
                        } else {
                            final T typedBody;
                            if (responseType == String.class) {
                                typedBody = (T) rawBody;
                            } else {
                                typedBody = mapper.readValue(rawBody, responseType);
                            }
                            future.complete(Result.success(new HttpClientResponse<>(resp.statusCode(), typedBody, headers)));
                        }
                    } catch (final Exception t) {
                        future.completeExceptionally(t);
                    }
                })
                .onFailure(future::completeExceptionally);
    }

    private HttpErrorResponse toHttpErrorResponse(final String rawBody, final int statusCode) {
        try {
            final ErrorMessageResponse errorMessage = mapper.readValue(rawBody, ErrorMessageResponse.class);
            return new HttpErrorResponse(errorMessage, statusCode);
        } catch (final Exception ignored) {
        }
        return new HttpErrorResponse(new ErrorMessageResponse(rawBody), statusCode);
    }

    private static String buildRequestUri(final URI uri, final Map<String, String> extraQueryParams) {
        final StringBuilder sb = new StringBuilder();
        sb.append(uri.getRawPath() != null ? uri.getRawPath() : "/");

        final String existingQuery = uri.getRawQuery();
        final boolean hasExisting = existingQuery != null && !existingQuery.isEmpty();
        final boolean hasExtra = !extraQueryParams.isEmpty();

        if (hasExisting || hasExtra) {
            sb.append("?");
            if (hasExisting) {
                sb.append(existingQuery);
            }
            boolean first = !hasExisting;
            for (final Map.Entry<String, String> entry : extraQueryParams.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }

        return sb.toString();
    }
}
