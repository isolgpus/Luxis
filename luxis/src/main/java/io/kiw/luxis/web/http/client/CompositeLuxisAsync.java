package io.kiw.luxis.web.http.client;

import io.kiw.luxis.result.Result;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Combines multiple {@link LuxisAsync} operations that share the same error type into a single
 * {@link LuxisAsync} that completes once every one of them has completed.
 *
 * <p>Each operation is registered against a label. The combined async resolves to a
 * {@code Map<String, Result<ERR, Object>>} keyed by those labels, where each value is the
 * {@link Result} of the corresponding operation. This lets a single {@code asyncMap} step fire
 * several async calls in parallel, wait for all of them, and then leave it to the caller to decide
 * what to do with the mix of successes and failures — collapse the whole thing to an error if any
 * one failed, or accept that some did not work and carry on with the rest:
 *
 * <pre>{@code
 * .<Map<String, Result<HttpErrorResponse, Object>>>asyncMap(ctx ->
 *         CompositeLuxisAsync.<HttpErrorResponse>create()
 *                 .add("user", httpClient.get("/users/" + ctx.in().userId(), User.class))
 *                 .add("orders", httpClient.get("/orders/" + ctx.in().userId(), Orders.class))
 *                 .combine())
 * .flatMap(ctx -> {
 *     Map<String, Result<HttpErrorResponse, Object>> results = ctx.in();
 *     // collapse: fail the whole step if either call failed
 *     return results.get("user").flatMap(user ->
 *             results.get("orders").map(orders ->
 *                     new ProfileResponse((User) user, (Orders) orders)));
 * })
 * }</pre>
 *
 * <p>All registered operations are always awaited, and a failure of one does not stop the others.
 * The combined async only fails (completes exceptionally) if one of its operations completes
 * exceptionally — which the pipeline surfaces through its exception handler. A graceful
 * {@code Result.error} from any operation is captured in the map for the caller to handle.
 *
 * @param <ERR> the shared error type of every registered operation
 */
public final class CompositeLuxisAsync<ERR> {

    private final Map<String, LuxisAsync<Object, ERR>> asyncs = new LinkedHashMap<>();

    private CompositeLuxisAsync() {
    }

    public static <ERR> CompositeLuxisAsync<ERR> create() {
        return new CompositeLuxisAsync<>();
    }

    public CompositeLuxisAsync<ERR> add(final String label, final LuxisAsync<?, ERR> async) {
        asyncs.put(label, async.map(value -> (Object) value));
        return this;
    }

    public LuxisAsync<Map<String, Result<ERR, Object>>, ERR> combine() {
        final Map<String, CompletableFuture<Result<ERR, Object>>> futures = new LinkedHashMap<>();
        asyncs.forEach((label, async) -> futures.put(label, async.toCompletableFuture()));

        final CompletableFuture<Result<ERR, Map<String, Result<ERR, Object>>>> combined = new CompletableFuture<>();
        final CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture<?>[0]);

        CompletableFuture.allOf(all).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                combined.completeExceptionally(throwable);
                return;
            }

            final Map<String, Result<ERR, Object>> values = new LinkedHashMap<>();
            futures.forEach((label, future) -> values.put(label, future.join()));
            combined.complete(Result.success(values));
        });

        return new LuxisAsync<>(combined);
    }
}
