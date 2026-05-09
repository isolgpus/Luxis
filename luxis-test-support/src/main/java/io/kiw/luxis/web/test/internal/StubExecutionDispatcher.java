package io.kiw.luxis.web.test.internal;

import io.kiw.luxis.web.internal.ExecutionDispatcher;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class StubExecutionDispatcher implements ExecutionDispatcher {
    @Override
    public void handleBlocking(final Runnable o) {
        Thread.currentThread().setName("Worker");
        o.run();
    }

    @Override
    public void handleOnApplicationContext(final Runnable o) {
        Thread.currentThread().setName("Application");
        o.run();
    }

    @Override
    public <T> void handleOnApplicationContext(final CompletableFuture<T> future, final Consumer<Exception> exceptionHandler, final Consumer<T> o) {
        Thread.currentThread().setName("Application");
        try {
            final T join = future.join();
            o.accept(join);
        } catch (Exception e) {
            exceptionHandler.accept(e);
        }
    }
}
