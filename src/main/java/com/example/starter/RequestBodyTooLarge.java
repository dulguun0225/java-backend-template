package com.example.starter;

/**
 * A request body over the configured limit, raised by {@link StrictJsonBodyConverter} before any member is read —
 * on a declared {@code Content-Length} over the limit, or once the body streams past it — and answered by
 * {@link ApiExceptionHandler} as 413 {@code request.too-large} with the limit as {@code max}. It carries the limit
 * and nothing the caller sent.
 */
final class RequestBodyTooLarge extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long max;

    RequestBodyTooLarge(long max) {
        super("request body over " + max + " bytes", null, false, false);
        this.max = max;
    }

    /** The limit, in bytes. */
    long max() {
        return max;
    }
}
