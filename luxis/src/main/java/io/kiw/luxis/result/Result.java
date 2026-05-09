package io.kiw.luxis.result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class Result<E, S> {

    public static <E, S> Result<E, S> success(final S value) {
        return new Success<>(value);
    }

    public static <E, S> Result<E, S> error(final E value) {
        return new Error<>(value);
    }

    public static <E, S> Result<Map<Integer, E>, List<S>> collapse(final List<Result<E, S>> results) {

        final Map<Integer, E> collectedErrors = new HashMap<>();
        final List<S> successes = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            final Result<E, S> result = results.get(i);

            if (result instanceof Result.Success<E, S>) {
                successes.add(((Success<E, S>) result).value);
            } else {
                collectedErrors.put(i, ((Error<E, S>) result).value);
            }
        }

        if (collectedErrors.isEmpty()) {
            return Result.success(successes);
        } else {
            return Result.error(collectedErrors);
        }
    }


    public abstract void consume(final Consumer<E> errorConsumer, final Consumer<S> successConsumer);

    public abstract <SR> Result<E, SR> flatMap(final Function<S, Result<E, SR>> e);

    public abstract <ER> Result<ER, S> mapError(final Function<E, ER> e);

    public abstract <R> R fold(final Function<E, R> errorConsumer, final Function<S, R> successConsumer);

    public abstract <SR> Result<E, SR> map(final Function<S, SR> s);


    protected static class Success<E, S> extends Result<E, S> {

        private final S value;

        public Success(final S value) {
            super();

            this.value = value;
        }


        @Override
        public void consume(final Consumer<E> errorConsumer, final Consumer<S> successConsumer) {
            successConsumer.accept(value);
        }

        @Override
        public <SR> Result<E, SR> flatMap(final Function<S, Result<E, SR>> e) {
            return e.apply(this.value);
        }

        @Override
        public <ER> Result<ER, S> mapError(final Function<E, ER> e) {
            return Result.success(value);
        }


        @Override
        public <R> R fold(final Function<E, R> errorConsumer, final Function<S, R> successConsumer) {
            return successConsumer.apply(value);
        }

        @Override
        public <SR> Result<E, SR> map(final Function<S, SR> s) {
            return Result.success(s.apply(value));
        }

    }

    protected static class Error<E, S> extends Result<E, S> {

        private final E value;

        protected Error(final E value) {
            super();

            this.value = value;
        }


        @Override
        public void consume(final Consumer<E> errorConsumer, final Consumer<S> successConsumer) {
            errorConsumer.accept(value);
        }

        @Override
        public <SR> Result<E, SR> flatMap(final Function<S, Result<E, SR>> e) {
            return Result.error(this.value);
        }

        @Override
        public <ER> Result<ER, S> mapError(final Function<E, ER> e) {
            return Result.error(e.apply(value));
        }

        @Override
        public <R> R fold(final Function<E, R> errorConsumer, final Function<S, R> successConsumer) {
            return errorConsumer.apply(value);
        }

        @Override
        public <SR> Result<E, SR> map(final Function<S, SR> s) {
            return Result.error(value);
        }

    }
}
