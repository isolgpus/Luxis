package io.kiw.luxis.result;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResultTest {

    // --- Factory methods ---

    @Test
    public void shouldCreateSuccessResult() {
        Result<String, Integer> result = Result.success(42);
        Integer value = result.fold(e -> null, s -> s);
        assertEquals(Integer.valueOf(42), value);
    }

    @Test
    public void shouldCreateErrorResult() {
        Result<String, Integer> result = Result.error("fail");
        String error = result.fold(e -> e, s -> null);
        assertEquals("fail", error);
    }

    // --- consume ---

    @Test
    public void shouldConsumeSuccessValue() {
        Result<String, Integer> result = Result.success(42);
        boolean[] successCalled = {false};
        boolean[] errorCalled = {false};
        result.consume(e -> errorCalled[0] = true, s -> successCalled[0] = true);
        assertTrue(successCalled[0]);
        assertFalse(errorCalled[0]);
    }

    @Test
    public void shouldConsumeErrorValue() {
        Result<String, Integer> result = Result.error("fail");
        boolean[] successCalled = {false};
        boolean[] errorCalled = {false};
        result.consume(e -> errorCalled[0] = true, s -> successCalled[0] = true);
        assertTrue(errorCalled[0]);
        assertFalse(successCalled[0]);
    }

    // --- map ---

    @Test
    public void shouldMapSuccessValue() {
        Result<String, Integer> result = Result.<String, Integer>success(5).map(v -> v * 2);
        assertEquals(Integer.valueOf(10), result.fold(e -> null, s -> s));
    }

    @Test
    public void shouldNotMapWhenError() {
        boolean[] called = {false};
        Result<String, Integer> result = Result.<String, Integer>error("fail").map(v -> {
            called[0] = true;
            return v * 2;
        });
        assertFalse(called[0]);
        assertEquals("fail", result.fold(e -> e, s -> null));
    }

    // --- flatMap ---

    @Test
    public void shouldFlatMapSuccessToSuccess() {
        Result<String, Integer> result = Result.<String, Integer>success(5).flatMap(v -> Result.success(v + 1));
        assertEquals(Integer.valueOf(6), result.fold(e -> null, s -> s));
    }

    @Test
    public void shouldFlatMapSuccessToError() {
        Result<String, Integer> result = Result.<String, Integer>success(5).flatMap(v -> Result.error("bad"));
        assertEquals("bad", result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldNotFlatMapWhenError() {
        boolean[] called = {false};
        Result<String, Integer> result = Result.<String, Integer>error("fail").flatMap(v -> {
            called[0] = true;
            return Result.success(99);
        });
        assertFalse(called[0]);
        assertEquals("fail", result.fold(e -> e, s -> null));
    }

    // --- mapError ---

    @Test
    public void shouldMapErrorValue() {
        Result<Integer, Integer> result = Result.<String, Integer>error("fail").mapError(String::length);
        assertEquals(Integer.valueOf(4), result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldNotMapErrorWhenSuccess() {
        boolean[] called = {false};
        Result<Integer, Integer> result = Result.<String, Integer>success(42).mapError(e -> {
            called[0] = true;
            return e.length();
        });
        assertFalse(called[0]);
        assertEquals(Integer.valueOf(42), result.fold(e -> null, s -> s));
    }

    // --- fold ---

    @Test
    public void shouldFoldSuccessToValue() {
        int value = Result.<String, Integer>success(10).fold(e -> -1, s -> s + 5);
        assertEquals(15, value);
    }

    @Test
    public void shouldFoldErrorToValue() {
        int value = Result.<String, Integer>error("err").fold(String::length, s -> -1);
        assertEquals(3, value);
    }

    // --- collapse ---

    @Test
    public void shouldCollapseAllSuccessesToSuccessList() {
        List<Result<String, Integer>> results = Arrays.asList(
                Result.success(1), Result.success(2), Result.success(3)
        );
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        List<Integer> values = collapsed.fold(e -> null, s -> s);
        assertEquals(Arrays.asList(1, 2, 3), values);
    }

    @Test
    public void shouldCollapseAllErrorsToErrorMap() {
        List<Result<String, Integer>> results = Arrays.asList(
                Result.error("a"), Result.error("b")
        );
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        Map<Integer, String> errors = collapsed.fold(e -> e, s -> null);
        assertEquals(2, errors.size());
        assertEquals("a", errors.get(0));
        assertEquals("b", errors.get(1));
    }

    @Test
    public void shouldCollapseMixedResultsToErrorMap() {
        List<Result<String, Integer>> results = Arrays.asList(
                Result.success(1), Result.error("x"), Result.success(3)
        );
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        Map<Integer, String> errors = collapsed.fold(e -> e, s -> null);
        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("x", errors.get(1));
    }

    @Test
    public void shouldCollapseEmptyListToSuccessWithEmptyList() {
        List<Result<String, Integer>> results = Collections.emptyList();
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        List<Integer> values = collapsed.fold(e -> null, s -> s);
        assertNotNull(values);
        assertTrue(values.isEmpty());
    }

    @Test
    public void shouldCollapseSingleSuccessToSuccessList() {
        List<Result<String, Integer>> results = Collections.singletonList(Result.success(42));
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        assertEquals(Collections.singletonList(42), collapsed.fold(e -> null, s -> s));
    }

    @Test
    public void shouldCollapseSingleErrorToErrorMap() {
        List<Result<String, Integer>> results = Collections.singletonList(Result.error("x"));
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        Map<Integer, String> errors = collapsed.fold(e -> e, s -> null);
        assertEquals(1, errors.size());
        assertEquals("x", errors.get(0));
    }

    @Test
    public void shouldCollapsePreservingErrorIndices() {
        List<Result<String, Integer>> results = Arrays.asList(
                Result.success(1), Result.success(2), Result.error("c"), Result.success(4), Result.error("e")
        );
        Result<Map<Integer, String>, List<Integer>> collapsed = Result.collapse(results);
        Map<Integer, String> errors = collapsed.fold(e -> e, s -> null);
        assertEquals(2, errors.size());
        assertEquals("c", errors.get(2));
        assertEquals("e", errors.get(4));
    }

    // --- Chaining ---

    @Test
    public void shouldChainMapThenFlatMapOnSuccess() {
        Result<String, Integer> result = Result.<String, Integer>success(3)
                .map(v -> v * 10)
                .flatMap(v -> Result.success(v + 1));
        assertEquals(Integer.valueOf(31), result.fold(e -> null, s -> s));
    }

    @Test
    public void shouldChainMapThenFlatMapReturningError() {
        Result<String, Integer> result = Result.<String, Integer>success(3)
                .map(v -> v * 10)
                .flatMap(v -> Result.error("too big"));
        assertEquals("too big", result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldShortCircuitOnFlatMapError() {
        boolean[] mapCalled = {false};
        Result<String, Integer> result = Result.<String, Integer>success(1)
                .flatMap(v -> Result.<String, Integer>error("stop"))
                .map(v -> {
                    mapCalled[0] = true;
                    return v + 100;
                });
        assertFalse(mapCalled[0]);
        assertEquals("stop", result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldShortCircuitMultipleFlatMaps() {
        boolean[] thirdCalled = {false};
        Result<String, Integer> result = Result.<String, Integer>success(1)
                .flatMap(v -> Result.success(v + 1))
                .flatMap(v -> Result.<String, Integer>error("halt"))
                .flatMap(v -> {
                    thirdCalled[0] = true;
                    return Result.success(v + 1);
                });
        assertFalse(thirdCalled[0]);
        assertEquals("halt", result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldTransformErrorThroughMapErrorInChain() {
        Result<String, Integer> result = Result.<String, Integer>error("fail")
                .flatMap(v -> Result.success(v + 1))
                .mapError(String::toUpperCase);
        assertEquals("FAIL", result.fold(e -> e, s -> null));
    }

    @Test
    public void shouldIgnoreMapErrorOnSuccessChain() {
        Result<String, Integer> result = Result.<String, Integer>success(5)
                .map(v -> v * 2)
                .mapError(e -> "won't happen");
        assertEquals(Integer.valueOf(10), result.fold(e -> null, s -> s));
    }

    @Test
    public void shouldTransformTypeThroughChain() {
        Result<String, Integer> result = Result.<String, Integer>success(42)
                .map(v -> "value=" + v)
                .flatMap(s -> Result.success(s.length()));
        assertEquals(Integer.valueOf(8), result.fold(e -> null, s -> s));
    }

    // --- Edge cases ---

    @Test
    public void shouldAllowNullSuccessValue() {
        Result<String, Integer> result = Result.success(null);
        assertNull(result.fold(e -> "not null", s -> s));
    }

    @Test
    public void shouldAllowNullErrorValue() {
        Result<String, Integer> result = Result.error(null);
        assertNull(result.fold(e -> e, s -> "not null"));
    }

    @Test
    public void shouldMapSuccessToNull() {
        Result<String, Integer> result = Result.<String, Integer>success(5).map(v -> null);
        assertNull(result.fold(e -> -1, s -> s));
    }
}
