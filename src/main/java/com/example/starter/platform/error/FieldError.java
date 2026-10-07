package com.example.starter.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

/**
 * One field-level validation failure: the input it names, a stable {@code code} from a {@link FieldCode} catalog,
 * and the {@code params} that code declares — what is allowed, by name, such as {@code {"max": 100}} on
 * {@code validation.too-long}. A list of these is the {@code errors} array of a {@code validation.failed} (400)
 * problem. {@code params} is omitted from the wire when the code declares none.
 *
 * <p>The input is named one of two ways, never both: a request-body member by its RFC 6901 {@code pointer}, or a
 * path variable, query parameter or header by OpenAPI's own parameter identity, {@code in} ({@code path},
 * {@code query}, {@code header}) and {@code name}. A header's {@code name} is the spelling the document declares,
 * never the one sent. A rule over several members is one entry at the pointer of the object holding them,
 * {@code ""} for the whole body.
 *
 * <p>An optional {@code detail} is caller-safe text the raiser chooses — {@code expected boolean} on a
 * {@code validation.wrong-type} entry — and is omitted from the wire when absent. Neither it nor a param is ever
 * built from the value the caller sent.
 *
 * <p>Built through {@link #of(String, FieldParams)} or {@link #ofParameter(In, String, FieldParams)}, which take the
 * code from the params record, so a code is never raised without its params. The constructors stay for test oracles
 * pinning a literal wire code; they refuse a code that is not the params record's own, and
 * {@code BanListArchTest.fieldErrorsAreBuiltFromTheirParams} keeps main code off them.
 */
public record FieldError(
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String pointer,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable In in,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String name,
        String code,

        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = NoParams.class)
        FieldParams params,

        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String detail) {

    /** Where a request input outside the body travels: OpenAPI's parameter locations, as written on the wire. */
    public enum In {
        PATH("path"),
        QUERY("query"),
        HEADER("header");

        private final String wire;

        In(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }
    }

    public FieldError {
        if ((in == null) != (name == null)) {
            throw new IllegalArgumentException("a parameter is named by both its location and its name");
        }
        if ((pointer == null) == (in == null)) {
            throw new IllegalArgumentException(
                    "an entry names a body member by pointer or a parameter by location and name, exactly one");
        }
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

    /** A body-member entry, for test oracles pinning a literal wire code. */
    public FieldError(String pointer, String code, FieldParams params, @Nullable String detail) {
        this(pointer, null, null, code, params, detail);
    }

    /** An entry at the body member {@code pointer} with the code its params record names, carrying those params. */
    public static FieldError of(String pointer, FieldParams params) {
        return new FieldError(pointer, null, null, params.code().wire(), params, null);
    }

    /** The same, with caller-safe text saying what was expected; never built from the value sent. */
    public static FieldError of(String pointer, FieldParams params, String detail) {
        return new FieldError(pointer, null, null, params.code().wire(), params, detail);
    }

    /** An entry naming the parameter {@code name} in {@code in}, with the code its params record names. */
    public static FieldError ofParameter(In in, String name, FieldParams params) {
        return new FieldError(null, in, name, params.code().wire(), params, null);
    }

    /** The same, with caller-safe text saying what was expected; never built from the value sent. */
    public static FieldError ofParameter(In in, String name, FieldParams params, String detail) {
        return new FieldError(null, in, name, params.code().wire(), params, detail);
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
