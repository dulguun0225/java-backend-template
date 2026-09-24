package com.example.starter.platform.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

/**
 * A request body as the strict JSON binder read it: the bound value, when there is one, together with every
 * binding failure the read found — a member the schema does not declare ({@code validation.unknown-field}), a
 * path identifier sent in the body ({@code validation.identifier-in-path}), a value of the wrong JSON type
 * ({@code validation.wrong-type}).
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

    private BoundBody(@Nullable T value, List<FieldError> bindingErrors) {
        this.value = value;
        this.bindingErrors = List.copyOf(bindingErrors);
    }

    /** A body that bound to {@code value}, with the binding failures the read collected (possibly none). */
    public static <T> BoundBody<T> of(T value, List<FieldError> bindingErrors) {
        return new BoundBody<>(value, bindingErrors);
    }

    /** A body that bound to no value at all; at least one binding failure says why. */
    public static <T> BoundBody<T> unbound(List<FieldError> bindingErrors) {
        if (bindingErrors.isEmpty()) {
            throw new IllegalArgumentException("an unbound body must name at least one binding failure");
        }
        return new BoundBody<>(null, bindingErrors);
    }

    /** An optional body the request did not send: {@code empty} is the value an absent body means. */
    public static <T> BoundBody<T> absent(T empty) {
        return new BoundBody<>(empty, List.of());
    }

    /**
     * Runs the field rules over the bound value and returns what they produce, or throws one
     * {@link ValidationFailed} naming every binding and rule failure. The rules append their failures to the
     * list they are given and may return {@code null} when they appended any; they are not run on an unbound
     * body. A rule that throws {@link ValidationFailed} itself has its failures merged the same way.
     */
    public <R> R validate(BiFunction<? super T, List<FieldError>, @Nullable R> rules) {
        List<FieldError> binding = sortedDistinct(bindingErrors);
        T bound = value;
        if (bound == null) {
            throw new ValidationFailed(binding);
        }
        List<FieldError> ruleErrors = new ArrayList<>();
        @Nullable R result;
        try {
            result = rules.apply(bound, ruleErrors);
        } catch (ValidationFailed thrown) {
            ruleErrors.addAll(thrown.errors());
            result = null;
        }
        Set<String> bindingPointers = new LinkedHashSet<>();
        binding.forEach(error -> bindingPointers.add(error.pointer()));
        List<FieldError> all = new ArrayList<>(binding);
        for (FieldError error : ruleErrors) {
            if (!bindingPointers.contains(error.pointer()) && !all.contains(error)) {
                all.add(error);
            }
        }
        if (!all.isEmpty()) {
            throw new ValidationFailed(all);
        }
        if (result == null) {
            throw new IllegalStateException("the field rules produced no value and reported no failure");
        }
        return result;
    }

    private static List<FieldError> sortedDistinct(List<FieldError> errors) {
        return errors.stream()
                .distinct()
                .sorted(Comparator.comparing(FieldError::pointer).thenComparing(FieldError::code))
                .toList();
    }
}
