package io.kiw.luxis.web.internal;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ExecutionDispatcher {
    void handleBlocking(Runnable o);

    void handleOnApplicationContext(Runnable o);

    <T> void handleOnApplicationContext(CompletableFuture<T> future, Consumer<Exception> exceptionHandler, Consumer<T> o);
}
