package com.example.starter.platform.error;

/**
 * The typed params of one field-level failure: a record, one per {@link FieldCode} constant, whose components are
 * the params that code declares, by name and in order, and which names that code back. A throw site builds the
 * record and passes it to {@link FieldError#of(String, FieldParams)}, which takes the wire code from it, so a code
 * cannot be raised without its params: the only way to name a code at a throw site is through its record's
 * constructor. A code that declares no params has a record with no components.
 *
 * <p>Params say what is allowed, never what was sent: {@code max} on {@code validation.too-long}, {@code expected}
 * on {@code validation.wrong-type}. They are serialised as the {@code params} object of the entry, omitted when the
 * record has no components. {@code ErrorCatalogSnapshotTest} holds every catalog constant and its record to each
 * other in both directions and snapshots each code's param names.
 */
public interface FieldParams {

    /** The catalog constant this record is the params of; that constant's {@link FieldCode#paramsType()} is it. */
    FieldCode code();
}
