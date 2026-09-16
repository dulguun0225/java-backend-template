package com.example.starter.platform;

/** A pagination cursor that is not one this service issued: tampered, truncated or from another endpoint. */
public final class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("invalid pagination cursor");
    }
}
