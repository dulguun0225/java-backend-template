package com.example.starter.platform.error;

/**
 * The compile-checked contract for a field-level validation sub-code: a stable machine {@code wire} string
 * naming which check a single field failed ({@code validation.required}, {@code validation.too-long}), and the
 * record type of the params every entry with that code carries ({@code max} for {@code validation.too-long}).
 * Deliberately not a {@link WireError}: a field code never stands alone as an HTTP response, it is always
 * collected into a parent {@code validation.failed} (400) problem, so it carries no status and the type
 * system forbids the confusion.
 */
public interface FieldCode {

    String wire();

    /**
     * The {@link FieldParams} record of this code: its components are the param names every entry with this code
     * carries, and its {@link FieldParams#code()} is this constant. A code with no params has a record with no
     * components.
     */
    Class<? extends FieldParams> paramsType();
}
