package com.example.starter;

import com.example.starter.platform.error.FieldCode;

/**
 * The cross-cutting field-level sub-codes, raised below any single feature, so no feature's own
 * {@code *FieldCode} catalog owns them: the three request-body binding failures {@link StrictJsonBodyConverter}
 * records for every body-taking endpoint — a member the operation's request type does not declare, a path
 * identifier sent in the body, and a value of the wrong JSON type. Each is an entry of a
 * {@code validation.failed} (400) problem, never a response on its own. Wire strings are immutable once
 * shipped, and {@code ErrorCatalogSnapshotTest} turns any change into a reviewable diff.
 */
public enum ApiFieldCode implements FieldCode {

    /** A request-body member the operation's request type does not declare; refused, never ignored. */
    UNKNOWN_FIELD("validation.unknown-field"),

    /**
     * A top-level request-body member named after one of the matched route's path variables: an identifier
     * travels in the path only, so the body never carries it, whatever its value.
     */
    IDENTIFIER_IN_PATH("validation.identifier-in-path"),

    /**
     * A request-body member whose JSON type is not the one its request type declares — a string where a boolean
     * is expected, an object where a string is. The entry's {@code detail} names the expected type.
     */
    WRONG_TYPE("validation.wrong-type");

    private final String wire;

    ApiFieldCode(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
