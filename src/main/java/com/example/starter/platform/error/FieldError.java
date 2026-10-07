package com.example.starter.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * One field-level validation failure: an RFC 6901 JSON pointer to the offending field, a stable {@code code} from
 * a {@link FieldCode} catalog, and the {@code params} that code declares — what is allowed, by name, such as
 * {@code {"max": 100}} on {@code validation.too-long}. A list of these is the {@code errors} array of a
 * {@code validation.failed} (400) problem. {@code params} is omitted from the wire when the code declares none.
 *
 * <p>An optional {@code detail} is caller-safe text the raiser chooses — {@code expected boolean} on a
 * {@code validation.wrong-type} entry — and is omitted from the wire when absent. Neither it nor a param is ever
 * built from the value the caller sent.
 *
 * <p>Built through {@link #of(String, FieldParams)}, which takes the code from the params record, so a code is
 * never raised without its params. The canonical constructor stays for test oracles pinning a literal wire code;
 * it refuses a code that is not the params record's own, and {@code BanListArchTest.fieldErrorsAreBuiltFromTheirParams}
 * keeps main code off it.
 */
public record FieldError(
        String pointer,
        String code,

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NoParams.class)
        FieldParams params,

        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String detail) {

    public FieldError {
        FieldCode declared = params.code();
        if (!declared.wire().equals(code)) {
            throw new IllegalArgumentException(
                    "field code " + code + " does not match its params record's code " + declared.wire());
        }
        if (declared.paramsType() != params.getClass()) {
            throw new IllegalArgumentException("params record "
                    + params.getClass().getName() + " is not the one field code " + declared.wire() + " declares");
        }
    }

    /** An entry at {@code pointer} with the code its params record names, carrying those params. */
    public static FieldError of(String pointer, FieldParams params) {
        return new FieldError(pointer, params.code().wire(), params, null);
    }

    /** The same, with caller-safe text saying what was expected; never built from the value sent. */
    public static FieldError of(String pointer, FieldParams params, String detail) {
        return new FieldError(pointer, params.code().wire(), params, detail);
    }

    /**
     * Jackson's inclusion filter for {@code params}: a params record with no components is left off the wire, so
     * an entry whose code declares no params has no {@code params} member. Jackson excludes a value this filter
     * is {@code equals} to.
     */
    static final class NoParams {

        @Override
        public boolean equals(@Nullable Object value) {
            return value instanceof Record record && record.getClass().getRecordComponents().length == 0;
        }

        @Override
        public int hashCode() {
            return NoParams.class.hashCode();
        }
    }
}
