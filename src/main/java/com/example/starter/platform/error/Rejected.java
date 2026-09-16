package com.example.starter.platform.error;

/**
 * A coded business rejection a feature raises; rendered at the edge as an RFC 9457 problem with the catalog
 * code, exactly like {@link ValidationFailed}.
 */
public final class Rejected extends RuntimeException {

    private final transient WireError code;

    public Rejected(WireError code) {
        super(code.wire());
        this.code = code;
    }

    public WireError code() {
        return code;
    }
}
