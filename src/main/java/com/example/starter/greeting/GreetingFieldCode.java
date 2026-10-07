package com.example.starter.greeting;

import com.example.starter.platform.error.FieldCode;
import com.example.starter.platform.error.FieldParams;

/**
 * The greeting feature's field-level validation sub-codes, each naming the {@link FieldParams} record its entries
 * carry, nested here.
 */
public enum GreetingFieldCode implements FieldCode {
    REQUIRED("validation.required", Required.class),
    TOO_LONG("validation.too-long", TooLong.class);

    private final String wire;
    private final Class<? extends FieldParams> paramsType;

    GreetingFieldCode(String wire, Class<? extends FieldParams> paramsType) {
        this.wire = wire;
        this.paramsType = paramsType;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Override
    public Class<? extends FieldParams> paramsType() {
        return paramsType;
    }

    /** {@code validation.required}: no params. */
    public record Required() implements FieldParams {
        @Override
        public FieldCode code() {
            return REQUIRED;
        }
    }

    /** {@code validation.too-long}: {@code max} is the longest value accepted, in characters (Unicode code points). */
    public record TooLong(int max) implements FieldParams {
        @Override
        public FieldCode code() {
            return TOO_LONG;
        }
    }
}
