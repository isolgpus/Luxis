package io.kiw.luxis.web.validation;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.pipeline.ErrorCause;
import io.kiw.luxis.web.pipeline.ErrorMessageResponseMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class Validator<T, ERR> {
    final T value;
    private final String prefix;
    private final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper;
    final Map<String, List<String>> errors = new LinkedHashMap<>();

    public Validator(final T value, final String prefix, final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper) {
        this.value = value;
        this.prefix = prefix;
        this.errorMessageResponseMapper = errorMessageResponseMapper;
    }


    public FieldChain field(final String field, final Function<T, ?> getter) {
        Object resolved;
        try {
            resolved = getter.apply(value);
        } catch (final NullPointerException e) {
            resolved = null;
        }
        return new FieldChain(prefix + field, resolved, this);
    }

    public <N> Validator<T, ERR> field(final String name, final Function<T, N> getter, final Consumer<Validator<N, ERR>> block) {
        N nested;
        try {
            nested = getter.apply(value);
        } catch (final NullPointerException e) {
            nested = null;
        }
        if (nested != null) {
            final Validator<N, ERR> nestedValidator = new Validator<>(nested, prefix + name + ".", this.errorMessageResponseMapper);
            block.accept(nestedValidator);
            errors.putAll(nestedValidator.errors);
        }
        return this;
    }

    public <E> ListValidator<T, E, ERR> listField(final String name, final Function<T, List<E>> getter) {
        List<E> list;
        try {
            list = getter.apply(value);
        } catch (final NullPointerException e) {
            list = null;
        }
        return new ListValidator<>(prefix + name, list, this, this.errorMessageResponseMapper);
    }

    void addError(final String field, final String message) {
        errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }

    public Result<ERR, T> toResult() {
        if (errors.isEmpty()) {
            return Result.success(value);
        }
        return Result.error(errorMessageResponseMapper.map(new ErrorMessageResponse("Validation failed", errors), ErrorCause.VALIDATION_ERROR));
    }
}
