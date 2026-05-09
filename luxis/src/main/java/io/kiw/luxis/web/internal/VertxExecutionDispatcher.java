package io.kiw.luxis.web.internal;

import io.vertx.core.Vertx;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VertxExecutionDispatcher implements ExecutionDispatcher {
    private final Vertx vertx;

    public VertxExecutionDispatcher(final Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void handleBlocking(final Runnable o) {
        vertx.executeBlocking(() -> {
            o.run();
            return null;
        });
    }

    @Override
    public void handleOnApplicationContext(final Runnable o) {
        vertx.runOnContext(unused -> o.run());
    }

    @Override
    public <T> void handleOnApplicationContext(final CompletableFuture<T> future, final Consumer<Exception> exceptionHandler, final Consumer<T> o) {
        future.thenAccept(r -> {
                    vertx.runOnContext(unused -> o.accept(r));
                })
                .exceptionally(r -> {
                    vertx.runOnContext(unused -> exceptionHandler.accept(new RuntimeException(r)));
                    return null;
                });
    }
}
