package com.example.starter.platform;

/**
 * Thrown when a decimal wire field receives a JSON number instead of a string. Raised by
 * {@link StringDecimalDeserializer}; the API exception handler maps it to 400
 * {@code money.number-not-string}. Unchecked so it surfaces cleanly through Jackson and Spring.
 */
public final class DecimalNotStringException extends RuntimeException {

    private final String fieldName;

    public DecimalNotStringException(String fieldName) {
        super("decimal field must be a JSON string, not a number: " + fieldName);
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
