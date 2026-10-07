package com.example.starter.platform.error;

/**
 * The typed params of a response code: a record whose components are the params the problem carries beside its
 * {@code code}, by name, and which names that code back, as {@link FieldParams} does for a field code. The edge
 * builds the problem from the record, taking the code from it, so a code with params is never sent without them.
 * Params say what is allowed, never what was sent: {@code max} on {@code request.too-large}.
 */
public interface ProblemParams {

    /** The catalog constant this record is the params of; that constant's {@link WireError#paramsType()} is it. */
    WireError code();
}
