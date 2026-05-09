package io.kiw.luxis.web.http.client;

public record CorrelatedAsync<T, ERR>(long correlationId, LuxisAsync<T, ERR> async) {
}
