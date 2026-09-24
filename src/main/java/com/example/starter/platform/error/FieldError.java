package com.example.starter.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * One field-level validation failure: an RFC 6901 JSON pointer to the offending field plus a stable
 * {@code code} from a {@link FieldCode} catalog. A list of these is the {@code errors} array of a
 * {@code validation.failed} (400) problem.
 *
 * <p>An optional {@code detail} is caller-safe text the raiser chooses — {@code expected boolean} on a
 * {@code validation.wrong-type} entry — and is omitted from the wire when absent. It is never built from the
 * value the caller sent.
 *
 * <p>Construct via a factory so the code can only come from a compile-checked catalog; the canonical
 * constructors stay for test oracles pinning a literal.
 */
public record FieldError(
        String pointer,
        String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String detail) {

    /** An entry with no {@code detail}, the shape every rule failure takes. */
    public FieldError(String pointer, String code) {
        this(pointer, code, null);
    }

    public static FieldError of(String pointer, FieldCode code) {
        return new FieldError(pointer, code.wire());
    }

    /** An entry with caller-safe text saying what was expected; never built from the value sent. */
    public static FieldError of(String pointer, FieldCode code, String detail) {
        return new FieldError(pointer, code.wire(), detail);
    }
}
