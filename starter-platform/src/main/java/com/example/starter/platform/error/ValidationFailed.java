package com.example.starter.platform.error;

import java.util.List;

/**
 * A request rejected on field validation: carries the collected {@link FieldError}s. Thrown by a feature's
 * service or controller; the API exception handler renders it as 400 {@code validation.failed} with the
 * errors array. Unchecked, because a validation failure is a normal outcome, not a fault.
 */
public final class ValidationFailed extends RuntimeException {

    private final transient List<FieldError> errors;

    public ValidationFailed(List<FieldError> errors) {
        super("validation failed: " + errors);
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("a validation failure must name at least one field error");
        }
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
