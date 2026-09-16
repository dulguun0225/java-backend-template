package com.example.starter.platform.error;

/**
 * The RFC 9457 body for a coded rejection: a stable machine {@code code} plus its HTTP {@code status}. Built
 * from a {@link WireError} catalog entry via {@link #of}, never from an inline string.
 */
public record ProblemBody(String code, int status) {

    public static ProblemBody of(WireError error) {
        return new ProblemBody(error.wire(), error.status());
    }
}
