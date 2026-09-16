package com.example.starter.greeting;

import com.example.starter.platform.error.WireError;
import org.springframework.http.HttpStatus;

/** The greeting feature's response-code catalog. One wire string maps to one status across every catalog. */
public enum GreetingErrorCode implements WireError {
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND);

    private final String wire;
    private final HttpStatus status;

    GreetingErrorCode(String wire, HttpStatus status) {
        this.wire = wire;
        this.status = status;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Override
    public int status() {
        return status.value();
    }
}
