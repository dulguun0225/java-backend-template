package com.example.starter;

import com.example.starter.platform.error.FieldCode;
import com.example.starter.platform.error.FieldParams;

/**
 * The cross-cutting field-level sub-codes, raised below any single feature, so no feature's own
 * {@code *FieldCode} catalog owns them: the four request-body binding failures {@link StrictJsonBodyConverter}
 * records for every body-taking endpoint — a member the operation's request type does not declare, a path
 * identifier sent in the body, a value of the wrong JSON type, and a member given twice in one object. Each is an
 * entry of a {@code validation.failed} (400) problem, never a response on its own. Each constant names the
 * {@link FieldParams} record its entries carry, nested here; a throw site builds that record. Wire strings and
 * param names are immutable once shipped, and {@code ErrorCatalogSnapshotTest} turns any change into a reviewable
 * diff.
 */
public enum ApiFieldCode implements FieldCode {

    /** A request-body member the operation's request type does not declare; refused, never ignored. */
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
     * A member that appears twice in one object of a request body, at any depth, declared or not: two values for
     * one field contradict each other, so neither is bound. The pointer names the repeated member.
     */
    DUPLICATE_MEMBER("validation.duplicate-member", DuplicateMember.class);

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

    /** {@code validation.unknown-field}: no params; the pointer names the member. */
    public record UnknownField() implements FieldParams {
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
     * {@code integer}, {@code array} or {@code object}.
     */
    public record WrongType(String expected) implements FieldParams {
        @Override
        public FieldCode code() {
            return WRONG_TYPE;
        }
    }

    /** {@code validation.duplicate-member}: no params; the pointer names the repeated member. */
    public record DuplicateMember() implements FieldParams {
        @Override
        public FieldCode code() {
            return DUPLICATE_MEMBER;
        }
    }
}
