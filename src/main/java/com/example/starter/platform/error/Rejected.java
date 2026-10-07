package com.example.starter.platform.error;

/**
 * A coded business rejection a feature raises; rendered at the edge as an RFC 9457 problem with the catalog
 * code, exactly like {@link ValidationFailed}. It carries no params, so it refuses a code that declares some.
 */
public final class Rejected extends RuntimeException {

    private final transient WireError code;

    public Rejected(WireError code) {
        super(code.wire());
        if (code.paramsType().getRecordComponents().length > 0) {
            throw new IllegalArgumentException(
                    code.wire() + " carries params; a rejection without them would send it incomplete");
        }
        this.code = code;
    }

    public WireError code() {
        return code;
    }
}
