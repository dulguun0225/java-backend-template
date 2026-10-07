package com.example.starter;

import com.example.starter.platform.error.FieldCode;
import com.example.starter.platform.error.FieldParams;
import java.util.List;

/**
 * The cross-cutting field-level sub-codes, raised below any single feature, so no feature's own
 * {@code *FieldCode} catalog owns them: the request-body binding failures {@link StrictJsonBodyConverter} records
 * for every body-taking endpoint — a member the operation's request type does not declare, a path identifier sent
 * in the body, a value of the wrong JSON type, a member given twice in one object — and the refusals of a path
 * variable, query parameter or header that {@link ApiExceptionHandler} and {@link DeclaredInputCheck} raise — an
 * input absent, an input that does not parse, a value outside its set, a query parameter not declared or given
 * twice. Each is an entry of a {@code validation.failed} (400) problem, never a response on its own. Each constant
 * names the {@link FieldParams} record its entries carry, nested here; a throw site builds that record. Wire
 * strings and param names are immutable once shipped, and {@code ErrorCatalogSnapshotTest} turns any change into a
 * reviewable diff.
 */
public enum ApiFieldCode implements FieldCode {

    /**
     * A request-body member the operation's request type does not declare, or a query parameter the operation does
     * not declare; refused, never ignored. {@code allowed} lists what is declared there.
     */
    UNKNOWN_FIELD("validation.unknown-field", UnknownField.class),

    /**
     * A top-level request-body member named after one of the matched route's path variables: an identifier
     * travels in the path only, so the body never carries it, whatever its value.
     */
    IDENTIFIER_IN_PATH("validation.identifier-in-path", IdentifierInPath.class),

    /**
     * A request-body member whose JSON type is not the one its request type declares — a string where a boolean
     * is expected, an object where a string is. {@code expected} names the declared JSON type; the entry's
     * {@code detail} says the same in words.
     */
    WRONG_TYPE("validation.wrong-type", WrongType.class),

    /**
     * A member that appears twice in one object of a request body, at any depth, declared or not, or a query
     * parameter or single-valued header given twice: two values for one input contradict each other, so neither is
     * used.
     */
    DUPLICATE_MEMBER("validation.duplicate-member", DuplicateMember.class),

    /** A required query parameter or header whose name is not in the request. */
    REQUIRED("validation.required", Required.class),

    /**
     * The text of a path variable, query parameter or header, or a body value of the right JSON type, that does not
     * parse as its declared type or format, empty and blank included. {@code expected} is the schema's
     * {@code format}, else its {@code type}; the entry's {@code detail} says the same in words.
     */
    INVALID_VALUE("validation.invalid-value", InvalidValue.class),

    /** A value outside its declared enumeration; {@code allowed} lists the values, sorted. */
    UNKNOWN_VALUE("validation.unknown-value", UnknownValue.class);

    private final String wire;
    private final Class<? extends FieldParams> paramsType;

    ApiFieldCode(String wire, Class<? extends FieldParams> paramsType) {
        this.wire = wire;
        this.paramsType = paramsType;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Override
    public Class<? extends FieldParams> paramsType() {
        return paramsType;
    }

    /**
     * {@code validation.unknown-field}: {@code allowed} is the members the enclosing object's type declares, or the
     * query parameters the operation declares, sorted; taken from the declaration, never from the request.
     */
    public record UnknownField(List<String> allowed) implements FieldParams {
        @Override
        public FieldCode code() {
            return UNKNOWN_FIELD;
        }
    }

    /** {@code validation.identifier-in-path}: no params; the pointer names the member, which is a path variable. */
    public record IdentifierInPath() implements FieldParams {
        @Override
        public FieldCode code() {
            return IDENTIFIER_IN_PATH;
        }
    }

    /**
     * {@code validation.wrong-type}: {@code expected} is the declared JSON type — {@code string}, {@code boolean},
     * {@code integer}, {@code number}, {@code array} or {@code object}.
     */
    public record WrongType(String expected) implements FieldParams {
        @Override
        public FieldCode code() {
            return WRONG_TYPE;
        }
    }

    /** {@code validation.duplicate-member}: no params; the entry names the repeated member or parameter. */
    public record DuplicateMember() implements FieldParams {
        @Override
        public FieldCode code() {
            return DUPLICATE_MEMBER;
        }
    }

    /** {@code validation.required}: no params; the entry names the absent parameter. */
    public record Required() implements FieldParams {
        @Override
        public FieldCode code() {
            return REQUIRED;
        }
    }

    /**
     * {@code validation.invalid-value}: {@code expected} is the declared schema's {@code format} — {@code uuid},
     * {@code int32}, {@code int64}, {@code date}, {@code date-time} — else its {@code type}.
     */
    public record InvalidValue(String expected) implements FieldParams {
        @Override
        public FieldCode code() {
            return INVALID_VALUE;
        }
    }

    /** {@code validation.unknown-value}: {@code allowed} is the enumeration's values, sorted. */
    public record UnknownValue(List<String> allowed) implements FieldParams {
        @Override
        public FieldCode code() {
            return UNKNOWN_VALUE;
        }
    }
}
