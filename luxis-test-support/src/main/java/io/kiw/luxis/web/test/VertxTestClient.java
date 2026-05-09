package io.kiw.luxis.web.test;

import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpCookie;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VertxTestClient implements TestClient {

    private final Vertx vertx;
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;
    private final String host;
    private final int port;
    private final long timeoutSeconds;

    public VertxTestClient(final String host, final int port) {
        this.host = host;
        this.port = port;
        this.timeoutSeconds = 10;
        this.vertx = Vertx.vertx();
        this.httpClient = vertx.createHttpClient();
        this.webSocketClient = vertx.createWebSocketClient();
    }

    @Override
    public TestHttpResponse post(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.POST);
    }

    @Override
    public TestHttpResponse put(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.PUT);
    }

    @Override
    public TestHttpResponse delete(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.DELETE);
    }

    @Override
    public TestHttpResponse patch(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.PATCH);
    }

    @Override
    public TestHttpResponse get(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.GET);
    }

    @Override
    public TestHttpResponse options(final StubRequest stubRequest) {
        return request(stubRequest, HttpMethod.OPTIONS);
    }

    @Override
    public TestWebSocketClient webSocket(final StubRequest stubRequest) {
        return webSocketConnection(stubRequest);
    }

    @Override
    public void assertException(final String expected) {

    }

    @Override
    public void assertNoMoreExceptions() {

    }

    public TestWebSocketClient webSocketConnection(final StubRequest stubRequest) {
        final CompletableFuture<VertxTestWebSocketClient> future = new CompletableFuture<>();

        final String uri = buildUri(stubRequest);
        final WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setHost(host)
                .setPort(port)
                .setURI(uri);

        for (final Map.Entry<String, String> header : stubRequest.headers.entrySet()) {
            options.addHeader(header.getKey(), header.getValue());
        }

        webSocketClient.connect(options)
                .onSuccess(ws -> future.complete(new VertxTestWebSocketClient(ws)))
                .onFailure(future::completeExceptionally);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    private TestHttpResponse request(final StubRequest stubRequest, final HttpMethod method) {
        final CompletableFuture<TestHttpResponse> future = new CompletableFuture<>();

        final String uri = buildUri(stubRequest);

        httpClient.request(method, port, host, uri)
                .onSuccess(req -> {
                    for (final Map.Entry<String, String> header : stubRequest.headers.entrySet()) {
                        req.putHeader(header.getKey(), header.getValue());
                    }

                    for (final Map.Entry<String, HttpCookie> cookie : stubRequest.cookies.entrySet()) {
                        req.putHeader("Cookie", cookie.getValue().name() + "=" + cookie.getValue().value());
                    }

                    if (!stubRequest.fileUploads.isEmpty()) {
                        final String boundary = "----VertxHttpClientBoundary" + System.nanoTime();
                        req.putHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

                        final Buffer multipartBody = Buffer.buffer();
                        for (final Map.Entry<String, HttpBuffer> upload : stubRequest.fileUploads.entrySet()) {
                            multipartBody.appendString("--" + boundary + "\r\n");
                            multipartBody.appendString("Content-Disposition: form-data; name=\"" + upload.getKey() + "\"; filename=\"" + upload.getKey() + "\"\r\n");
                            multipartBody.appendString("Content-Type: application/octet-stream\r\n\r\n");
                            multipartBody.appendBuffer(Buffer.buffer(upload.getValue().bytes()));
                            multipartBody.appendString("\r\n");
                        }
                        multipartBody.appendString("--" + boundary + "--\r\n");

                        req.send(multipartBody)
                                .onSuccess(resp -> handleResponse(resp, future))
                                .onFailure(future::completeExceptionally);
                    } else if (stubRequest.body != null) {
                        req.putHeader("Content-Type", "application/json");
                        req.send(Buffer.buffer(stubRequest.body))
                                .onSuccess(resp -> handleResponse(resp, future))
                                .onFailure(future::completeExceptionally);
                    } else {
                        req.send()
                                .onSuccess(resp -> handleResponse(resp, future))
                                .onFailure(future::completeExceptionally);
                    }
                })
                .onFailure(future::completeExceptionally);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new RuntimeException("HTTP request failed: " + method + " " + uri, e);
        }
    }

    private void handleResponse(final HttpClientResponse resp, final CompletableFuture<TestHttpResponse> future) {
        resp.body()
                .onSuccess(body -> {
                    final TestHttpResponse testResponse = new TestHttpResponse(body.toString())
                            .withStatusCode(resp.statusCode());

                    for (final String headerName : resp.headers().names()) {
                        testResponse.withHeader(headerName, resp.getHeader(headerName));
                    }

                    if (resp.cookies() != null) {
                        for (final String setCookie : resp.cookies()) {
                            final String[] parts = setCookie.split(";")[0].split("=", 2);
                            if (parts.length == 2) {
                                testResponse.withCookie(parts[0].trim(), parts[1].trim());
                            }
                        }
                    }

                    future.complete(testResponse);
                })
                .onFailure(future::completeExceptionally);
    }

    private String buildUri(final StubRequest stubRequest) {
        final StringBuilder uri = new StringBuilder(stubRequest.path);

        if (!stubRequest.queryParams.isEmpty()) {
            uri.append("?");
            boolean first = true;
            for (final Map.Entry<String, String> entry : stubRequest.queryParams.entrySet()) {
                if (!first) {
                    uri.append("&");
                }
                if (entry.getValue() != null) {
                    uri.append(entry.getKey()).append("=").append(entry.getValue());
                }
                first = false;
            }
        }

        return uri.toString();
    }

    public void close() throws Exception {
        try {
            final CompletableFuture<Void> clientClose = new CompletableFuture<>();
            httpClient.close()
                    .onSuccess(v -> clientClose.complete(null))
                    .onFailure(clientClose::completeExceptionally);
            clientClose.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final Exception e) {
            // best effort
        }

        try {
            final CompletableFuture<Void> wsClientClose = new CompletableFuture<>();
            webSocketClient.close()
                    .onSuccess(v -> wsClientClose.complete(null))
                    .onFailure(wsClientClose::completeExceptionally);
            wsClientClose.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final Exception e) {
            // best effort
        }

        try {
            final CompletableFuture<Void> vertxClose = new CompletableFuture<>();
            vertx.close()
                    .onSuccess(v -> vertxClose.complete(null))
                    .onFailure(vertxClose::completeExceptionally);
            vertxClose.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (final Exception e) {
            // best effort
        }
    }
}
