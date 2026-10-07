package com.example.starter.platform.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A request body as the strict JSON binder read it: the bound value, when there is one, together with every
 * binding failure the read found — a member the schema does not declare ({@code validation.unknown-field}), a
 * path identifier sent in the body ({@code validation.identifier-in-path}), a value of the wrong JSON type
 * ({@code validation.wrong-type}), a member given twice ({@code validation.duplicate-member}) — and the number of
 * further failures the read found past {@link ValidationFailed#MAX_ERRORS} and did not record.
 *
 * <p>The value is reachable only through {@link #validate}: the feature's field rules receive it and append
 * their own failures, and one {@code validation.failed} then names every binding failure and every rule
 * failure of the request together. There is no getter, so no handler or service can act on a body whose
 * binding failed.
 *
 * <p>Binding failures come first, deduplicated and sorted by pointer, then the rule failures in the order the
 * rules found them, minus any at a pointer that already holds a binding failure: a member of the wrong type
 * binds as absent, and the rule's {@code validation.required} for it would name the same member twice.
 */
public final class BoundBody<T> {

    private final @Nullable T value;
    private final List<FieldError> bindingErrors;
    private final long omitted;

    private BoundBody(@Nullable T value, List<FieldError> bindingErrors, long omitted) {
        if (omitted < 0 || (omitted > 0 && bindingErrors.isEmpty())) {
            throw new IllegalArgumentException("failures are omitted only past recorded ones");
        }
        this.value = value;
        this.bindingErrors = List.copyOf(bindingErrors);
        this.omitted = omitted;
    }

    /** A body that bound to {@code value}, with the binding failures the read collected (possibly none). */
    public static <T> BoundBody<T> of(T value, List<FieldError> bindingErrors) {
        return new BoundBody<>(value, bindingErrors, 0);
    }

    /** The same, with the number of further binding failures the read found and did not record. */
    public static <T> BoundBody<T> of(T value, List<FieldError> bindingErrors, long omitted) {
        return new BoundBody<>(value, bindingErrors, omitted);
    }

    /** A body that bound to no value at all; at least one binding failure says why. */
    public static <T> BoundBody<T> unbound(List<FieldError> bindingErrors) {
        return unbound(bindingErrors, 0);
    }

    /** The same, with the number of further binding failures the read found and did not record. */
    public static <T> BoundBody<T> unbound(List<FieldError> bindingErrors, long omitted) {
        if (bindingErrors.isEmpty()) {
            throw new IllegalArgumentException("an unbound body must name at least one binding failure");
        }
        return new BoundBody<>(null, bindingErrors, omitted);
    }

    /** An optional body the request did not send: {@code empty} is the value an absent body means. */
    public static <T> BoundBody<T> absent(T empty) {
        return new BoundBody<>(empty, List.of(), 0);
    }

    /**
     * Runs the field rules over the bound value and returns what they produce, or throws one
     * {@link ValidationFailed} naming every binding and rule failure. The rules append their failures to the
     * list they are given and may return {@code null} when they appended any; they are not run on an unbound
     * body. A rule that throws {@link ValidationFailed} itself has its failures merged the same way. The failures
     * the read did not record, and any past {@link ValidationFailed#MAX_ERRORS} once merged, are counted in
     * {@link ValidationFailed#omitted()}.
     */
    public <R> R validate(BiFunction<? super T, List<FieldError>, @Nullable R> rules) {
        List<FieldError> binding = sortedDistinct(bindingErrors);
        T bound = value;
        if (bound == null) {
            throw new ValidationFailed(binding, omitted);
        }
        List<FieldError> ruleErrors = new ArrayList<>();
        long ruleOmitted = 0;
        @Nullable R result;
        try {
            result = rules.apply(bound, ruleErrors);
        } catch (ValidationFailed thrown) {
            ruleErrors.addAll(thrown.errors());
            ruleOmitted = thrown.omitted();
            result = null;
        }
        Set<String> bindingPointers = new LinkedHashSet<>();
        binding.forEach(error -> bindingPointers.add(sortKey(error)));
        List<FieldError> all = new ArrayList<>(binding);
        for (FieldError error : ruleErrors) {
            if (!bindingPointers.contains(sortKey(error)) && !all.contains(error)) {
                all.add(error);
            }
        }
        if (!all.isEmpty()) {
            throw new ValidationFailed(all, omitted + ruleOmitted);
        }
        if (result == null) {
            throw new IllegalStateException("the field rules produced no value and reported no failure");
        }
        return result;
    }

    private static List<FieldError> sortedDistinct(List<FieldError> errors) {
        return errors.stream()
                .distinct()
                .sorted(Comparator.comparing((FieldError error) -> sortKey(error))
                        .thenComparing(FieldError::code))
                .toList();
    }

    /**
     * The pointer of a body-member entry. A binding failure always names one; a rule's entry may name a parameter
     * instead, keyed {@code in:<location>:<name>}, which no pointer equals, since a pointer is empty or starts
     * with {@code /}.
     */
    private static String sortKey(FieldError error) {
        return Objects.requireNonNullElse(error.pointer(), "in:" + error.in() + ":" + error.name());
    }
}
