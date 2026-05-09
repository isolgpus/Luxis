package io.kiw.luxis.web.internal;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.test.internal.StubTimeoutScheduler;
import io.kiw.luxis.web.test.TimeInjector;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PendingAsyncResponsesTest {

    private static final long DEFAULT_TIMEOUT = 30_000;

    private final StubTimeoutScheduler scheduler = new StubTimeoutScheduler(new TimeInjector());
    private final List<Exception> exceptions = new ArrayList<>();

    private PendingAsyncResponses createPending() {
        return new PendingAsyncResponses(scheduler, exceptions::add);
    }

    @Test
    public void shouldAssignSequentialCorrelationIds() {
        final PendingAsyncResponses pending = createPending();

        final long id0 = pending.register(new CompletableFuture<>(), DEFAULT_TIMEOUT);
        final long id1 = pending.register(new CompletableFuture<>(), DEFAULT_TIMEOUT);
        final long id2 = pending.register(new CompletableFuture<>(), DEFAULT_TIMEOUT);

        Assert.assertEquals(0L, id0);
        Assert.assertEquals(1L, id1);
        Assert.assertEquals(2L, id2);
    }

    @Test
    public void shouldCompleteFutureWithSuccessResult() throws ExecutionException, InterruptedException {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, Integer>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        pending.complete(id, Result.success(42));

        Assert.assertTrue(future.isDone());
        final Result<HttpErrorResponse, Integer> result = future.get();
        result.consume(
                error -> Assert.fail("Expected success but got error"),
                value -> Assert.assertEquals(Integer.valueOf(42), value)
        );
    }

    @Test
    public void shouldCompleteFutureWithErrorResult() throws ExecutionException, InterruptedException {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        pending.complete(id, Result.error(new HttpErrorResponse(null, 400)));

        Assert.assertTrue(future.isDone());
        final Result<HttpErrorResponse, String> result = future.get();
        result.consume(
                error -> Assert.assertEquals(400, error.statusCode()),
                value -> Assert.fail("Expected error but got success")
        );
    }

    @Test
    public void shouldRemoveEntryAfterCompletion() {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, Integer>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        pending.complete(id, Result.success(1));

        try {
            pending.complete(id, Result.success(2));
            Assert.fail("Expected IllegalArgumentException for already-completed correlation ID");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(String.valueOf(id)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnknownCorrelationId() {
        final PendingAsyncResponses pending = createPending();
        pending.complete(999L, Result.success("value"));
    }

    @Test
    public void shouldHandleMultiplePendingResponses() throws ExecutionException, InterruptedException {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future0 = new CompletableFuture<>();
        final CompletableFuture<Result<HttpErrorResponse, String>> future1 = new CompletableFuture<>();
        final CompletableFuture<Result<HttpErrorResponse, String>> future2 = new CompletableFuture<>();

        final long id0 = pending.register(future0, DEFAULT_TIMEOUT);
        final long id1 = pending.register(future1, DEFAULT_TIMEOUT);
        final long id2 = pending.register(future2, DEFAULT_TIMEOUT);

        // Complete out of order
        pending.complete(id2, Result.success("third"));
        pending.complete(id0, Result.success("first"));
        pending.complete(id1, Result.success("second"));

        future0.get().consume(e -> Assert.fail(), v -> Assert.assertEquals("first", v));
        future1.get().consume(e -> Assert.fail(), v -> Assert.assertEquals("second", v));
        future2.get().consume(e -> Assert.fail(), v -> Assert.assertEquals("third", v));
    }

    @Test
    public void timeoutCompletesFutureWithErrorAfterDelay() throws ExecutionException, InterruptedException {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        pending.register(future, DEFAULT_TIMEOUT);

        Assert.assertFalse(future.isDone());

        scheduler.advanceBy(30_001);

        Assert.assertTrue(future.isDone());
        final Result<HttpErrorResponse, String> result = future.get();
        result.consume(
                error -> {
                    Assert.assertEquals(500, error.statusCode());
                    Assert.assertEquals("Something went wrong", error.errorMessageValue().message());
                },
                value -> Assert.fail("Expected error but got success")
        );
    }

    @Test
    public void timeoutInvokesExceptionHandler() {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        scheduler.advanceBy(30_001);

        Assert.assertEquals(1, exceptions.size());
        Assert.assertTrue(exceptions.get(0).getMessage().contains("timed out"));
        Assert.assertTrue(exceptions.get(0).getMessage().contains(String.valueOf(id)));
    }

    @Test
    public void timeoutRemovesEntryFromPending() {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        scheduler.advanceBy(30_001);

        try {
            pending.complete(id, Result.success("late"));
            Assert.fail("Expected IllegalArgumentException after timeout removed the entry");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(String.valueOf(id)));
        }
    }

    @Test
    public void normalCompletionCancelsTimeout() {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        pending.complete(id, Result.success("done"));

        // Advancing past timeout should not invoke exception handler
        scheduler.advanceBy(30_001);

        Assert.assertTrue(exceptions.isEmpty());
    }

    @Test
    public void doubleCompleteAfterTimeoutThrows() {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        final long id = pending.register(future, DEFAULT_TIMEOUT);

        scheduler.advanceBy(30_001);

        try {
            pending.complete(id, Result.success("late"));
            Assert.fail("Expected IllegalArgumentException for already-timed-out correlation ID");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains(String.valueOf(id)));
        }
    }

    @Test
    public void customTimeoutRespectsProvidedDuration() throws ExecutionException, InterruptedException {
        final PendingAsyncResponses pending = createPending();
        final CompletableFuture<Result<HttpErrorResponse, String>> future = new CompletableFuture<>();
        pending.register(future, 1_000);

        Assert.assertFalse(future.isDone());

        scheduler.advanceBy(999);
        Assert.assertFalse(future.isDone());

        scheduler.advanceBy(2);
        Assert.assertTrue(future.isDone());

        final Result<HttpErrorResponse, String> result = future.get();
        result.consume(
                error -> Assert.assertEquals(500, error.statusCode()),
                value -> Assert.fail("Expected error but got success")
        );
    }
}
