package com.example.starter.platform.error;

/**
 * One field-level validation failure: an RFC 6901 JSON pointer to the offending field plus a stable
 * {@code code} from a {@link FieldCode} catalog. A list of these is the {@code errors} array of a
 * {@code validation.failed} (400) problem. Construct via {@link #of(String, FieldCode)} so the code can only
 * come from a compile-checked catalog; the canonical constructor stays for test oracles pinning a literal.
 */
public record FieldError(String pointer, String code) {

    public static FieldError of(String pointer, FieldCode code) {
        return new FieldError(pointer, code.wire());
    }
}
