package com.example.starter;

import org.jspecify.annotations.Nullable;

/**
 * Why a request body could not be read as a JSON object at all, carried as the cause of the
 * {@code HttpMessageNotReadableException} {@link StrictJsonBodyConverter} raises, so that
 * {@link ApiExceptionHandler} can say in {@code validation.malformed-body}'s {@code detail} what was wrong and
 * where. It carries a kind and a position and nothing the caller sent.
 */
final class UnreadableBody extends RuntimeException {

    private static final long serialVersionUID = 1L;

    enum Kind {
        /** No JSON value at all: an empty or whitespace-only body. */
        MISSING,

        /** Well-formed JSON whose top-level value is not an object. */
        NOT_AN_OBJECT,

        /** Not well-formed JSON; the position is where the reader stopped, when it knows. */
        NOT_WELL_FORMED
    }

    private final Kind kind;
    private final int line;
    private final int column;

    UnreadableBody(Kind kind) {
        this(kind, -1, -1);
    }

    UnreadableBody(Kind kind, int line, int column) {
        super("request body unreadable: " + kind, null, false, false);
        this.kind = kind;
        this.line = line;
        this.column = column;
    }

    Kind kind() {
        return kind;
    }

    /** The caller-safe sentence for {@code detail}: the kind and, when known, the line and column. */
    String detail() {
        return switch (kind) {
            case MISSING -> "The request body is missing.";
            case NOT_AN_OBJECT -> "The request body must be a JSON object.";
            case NOT_WELL_FORMED ->
                line > 0 && column > 0
                        ? "The request body is not well-formed JSON at line " + line + ", column " + column + "."
                        : "The request body is not well-formed JSON.";
        };
    }

    static @Nullable UnreadableBody in(Throwable ex) {
        Throwable cause = ex;
        for (int hops = 0; cause != null && hops < 32; hops++) {
            if (cause instanceof UnreadableBody unreadable) {
                return unreadable;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
