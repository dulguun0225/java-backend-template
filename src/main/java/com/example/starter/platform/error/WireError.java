package com.example.starter.platform.error;

/**
 * The compile-checked RFC 9457 wire-error contract: a stable machine {@code wire} code plus its HTTP
 * status. Every feature's {@code *ErrorCode} enum implements this, so the render edge ({@link Rejected} through
 * {@code ApiExceptionHandler}) and the committed catalog snapshot ({@code ErrorCatalogSnapshotTest}) consume the interface and never an
 * inline string literal. Wire strings are immutable once shipped: clients branch on them.
 */
public interface WireError {

    String wire();

    int status();
}
