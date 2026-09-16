package com.example.starter.greeting;

import com.example.starter.platform.error.FieldCode;

/** The greeting feature's field-level validation sub-codes. */
public enum GreetingFieldCode implements FieldCode {
    REQUIRED("validation.required"),
    TOO_LONG("validation.too-long");

    private final String wire;

    GreetingFieldCode(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
