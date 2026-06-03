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
 * {@code Map<String, Object>} keyed by those labels, where each value is the success value of the
 * corresponding operation. This lets a single {@code asyncMap} step fire several async calls in
 * parallel and wait for all of them before continuing:
 *
 * <pre>{@code
 * .asyncMap(ctx -> CompositeLuxisAsync.<HttpErrorResponse>create()
 *         .add("user", httpClient.get("/users/" + ctx.in().userId(), User.class))
 *         .add("orders", httpClient.get("/orders/" + ctx.in().userId(), Orders.class))
 *         .combine())
 * .map(ctx -> {
 *     Map<String, Object> results = ctx.in();
 *     User user = (User) results.get("user");
 *     Orders orders = (Orders) results.get("orders");
 *     return new ProfileResponse(user, orders);
 * })
 * }</pre>
 *
 * <p>All registered operations are always awaited. If any of them resolves to an error, the
 * combined async resolves to the first error encountered (in registration order). If any of them
 * completes exceptionally, the combined async completes exceptionally too, which the pipeline
 * surfaces through its exception handler.
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

    public LuxisAsync<Map<String, Object>, ERR> combine() {
        final Map<String, CompletableFuture<Result<ERR, Object>>> futures = new LinkedHashMap<>();
        asyncs.forEach((label, async) -> futures.put(label, async.toCompletableFuture()));

        final CompletableFuture<Result<ERR, Map<String, Object>>> combined = new CompletableFuture<>();
        final CompletableFuture<?>[] all = futures.values().toArray(new CompletableFuture<?>[0]);

        CompletableFuture.allOf(all).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                combined.completeExceptionally(throwable);
                return;
            }

            final Map<String, Object> values = new LinkedHashMap<>();
            for (final Map.Entry<String, CompletableFuture<Result<ERR, Object>>> entry : futures.entrySet()) {
                final Result<ERR, Map<String, Object>> error = entry.getValue().join().fold(
                        failure -> Result.<ERR, Map<String, Object>>error(failure),
                        success -> {
                            values.put(entry.getKey(), success);
                            return null;
                        });
                if (error != null) {
                    combined.complete(error);
                    return;
                }
            }
            combined.complete(Result.success(values));
        });

        return new LuxisAsync<>(combined);
    }
}
