package io.kiw.luxis.web.validation;

import io.kiw.luxis.web.pipeline.ErrorMessageResponseMapper;

import java.util.List;
import java.util.function.Consumer;

public class ListValidator<P, E, ERR> {
    private final String fieldName;
    private final List<E> list;
    private final Validator<P, ERR> parent;
    private final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper;

    ListValidator(final String fieldName, final List<E> list, final Validator<P, ERR> parent, final ErrorMessageResponseMapper<ERR> errorMessageResponseMapper) {
        this.fieldName = fieldName;
        this.list = list;
        this.parent = parent;
        this.errorMessageResponseMapper = errorMessageResponseMapper;
    }

    public ListValidator<P, E, ERR> required() {
        if (list == null) parent.addError(fieldName, "must not be null");
        return this;
    }

    public ListValidator<P, E, ERR> minSize(final int min) {
        if (list != null && list.size() < min) {
            parent.addError(fieldName, "must have at least " + min + " items");
        }
        return this;
    }

    public ListValidator<P, E, ERR> maxSize(final int max) {
        if (list != null && list.size() > max) {
            parent.addError(fieldName, "must have at most " + max + " items");
        }
        return this;
    }

    public Validator<P, ERR> each(final Consumer<Validator<E, ERR>> block) {
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                final String elementPrefix = fieldName + "[" + i + "].";
                final Validator<E, ERR> ev = new Validator<>(list.get(i), elementPrefix, errorMessageResponseMapper);
                block.accept(ev);
                parent.errors.putAll(ev.errors);
            }
        }
        return parent;
    }
}
