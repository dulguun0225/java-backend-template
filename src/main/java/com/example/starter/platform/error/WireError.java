package com.example.starter.platform.error;

/**
 * The compile-checked RFC 9457 wire-error contract: a stable machine {@code wire} code plus its HTTP
 * status. Every feature's {@code *ErrorCode} enum implements this, so the render edge ({@link Rejected} through
 * {@code ApiExceptionHandler}) and the committed catalog snapshot ({@code ErrorCatalogSnapshotTest}) consume the interface and never an
 * inline string literal. Wire strings are immutable once shipped: clients branch on them.
 *
 * <p>A code that carries params names a {@link ProblemParams} record, nested in its catalog, whose components are
 * the params the problem carries beside its {@code code} ({@code max} on {@code request.too-large}); the edge
 * builds the problem from that record, so the code never leaves without them. A code that carries none keeps the
 * default, {@link NoParams}.
 */
public interface WireError {

    String wire();

    int status();

    /**
     * The params record of this code: its components are the param names every problem with this code carries.
     * {@link NoParams}, with none, unless the code declares some; such a record implements {@link ProblemParams}
     * and names this constant back.
     */
    default Class<? extends Record> paramsType() {
        return NoParams.class;
    }

    /** The params of a code that carries none. */
    record NoParams() {}
}
