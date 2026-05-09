package io.kiw.luxis.web.http.client;

import io.kiw.luxis.web.http.HttpBuffer;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.websocket.ClientWebSocketRoutes;
import io.kiw.luxis.web.websocket.WebSocketSession;

public interface LuxisHttpClient {

    <APP, RESP> WebSocketSession<RESP> connectToWebSocket(String path, ClientWebSocketRoutes<APP, RESP> routes);

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> get(HttpClientRequest request, Class<T> responseType);

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> post(HttpClientRequest request, Class<T> responseType);

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> put(HttpClientRequest request, Class<T> responseType);

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> delete(HttpClientRequest request, Class<T> responseType);

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> patch(HttpClientRequest request, Class<T> responseType);

    default LuxisAsync<HttpClientResponse<String>, HttpErrorResponse> get(final String url) {
        return get(HttpClientRequest.request(url), String.class);
    }

    default LuxisAsync<HttpClientResponse<String>, HttpErrorResponse> post(final String url, final String body) {
        return post(HttpClientRequest.request(url, body), String.class);
    }

    default LuxisAsync<HttpClientResponse<String>, HttpErrorResponse> put(final String url, final String body) {
        return put(HttpClientRequest.request(url, body), String.class);
    }

    default LuxisAsync<HttpClientResponse<String>, HttpErrorResponse> delete(final String url) {
        return delete(HttpClientRequest.request(url), String.class);
    }

    default LuxisAsync<HttpClientResponse<String>, HttpErrorResponse> patch(final String url, final String body) {
        return patch(HttpClientRequest.request(url, body), String.class);
    }

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> get(final String url, final Class<T> responseType) {
        return get(HttpClientRequest.request(url), responseType);
    }

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> post(final String url, final String body, final Class<T> responseType) {
        return post(HttpClientRequest.request(url, body), responseType);
    }

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> put(final String url, final String body, final Class<T> responseType) {
        return put(HttpClientRequest.request(url, body), responseType);
    }

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> delete(final String url, final Class<T> responseType) {
        return delete(HttpClientRequest.request(url), responseType);
    }

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> patch(final String url, final String body, final Class<T> responseType) {
        return patch(HttpClientRequest.request(url, body), responseType);
    }

    <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> postFiles(HttpClientRequest request, Class<T> responseType);

    LuxisAsync<HttpClientResponse<HttpBuffer>, HttpErrorResponse> download(HttpClientRequest request);

    default <T> LuxisAsync<HttpClientResponse<T>, HttpErrorResponse> postFiles(final String url, final Class<T> responseType) {
        return postFiles(HttpClientRequest.request(url), responseType);
    }

    default LuxisAsync<HttpClientResponse<HttpBuffer>, HttpErrorResponse> download(final String url) {
        return download(HttpClientRequest.request(url));
    }
}
