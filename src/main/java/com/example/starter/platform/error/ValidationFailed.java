package com.example.starter.platform.error;

import java.util.List;

/**
 * A request rejected on field validation: carries the collected {@link FieldError}s. Thrown by a feature's
 * service or controller; the API exception handler renders it as 400 {@code validation.failed} with the
 * errors array. Unchecked, because a validation failure is a normal outcome, not a fault.
 *
 * <p>It carries at most {@link #MAX_ERRORS} entries and counts the rest in {@link #omitted()}, which the edge
 * renders as {@code errorsOmitted}: one entry per undeclared member, each listing the members allowed, would
 * otherwise let a request buy a response many times its size. The strict body reader stops recording at the same
 * number, so the cap bounds what is held, not only what is sent.
 */
public final class ValidationFailed extends RuntimeException {

    /** The most entries one refusal carries; a per-project parameter, listed in {@code docs/GATES.md}. */
    public static final int MAX_ERRORS = 100;

    private final transient List<FieldError> errors;
    private final long omitted;

    public ValidationFailed(List<FieldError> errors) {
        this(errors, 0);
    }

    /**
     * The entries found and the number of further failures not recorded; entries past {@link #MAX_ERRORS} are
     * dropped and counted with them.
     */
    public ValidationFailed(List<FieldError> errors, long omitted) {
        super("validation failed: " + errors.size() + " entries, " + omitted + " omitted");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("a validation failure must name at least one field error");
        }
        if (omitted < 0) {
            throw new IllegalArgumentException("a negative number of omitted failures");
        }
        int kept = Math.min(errors.size(), MAX_ERRORS);
        this.errors = List.copyOf(errors.subList(0, kept));
        this.omitted = omitted + (errors.size() - kept);
    }

    public List<FieldError> errors() {
        return errors;
    }

    /** Failures found and not recorded as entries; {@code 0} when every one is. */
    public long omitted() {
        return omitted;
    }
}
