package com.example.starter;

import com.example.starter.platform.error.WireError;
import org.springframework.http.HttpStatus;

/**
 * The compile-checked catalog for cross-cutting API errors raised below any single feature: a malformed
 * body, a decimal sent as a JSON number, the standard MVC 4xx, a field-validation rejection, and the
 * last-resort 500. Feature-specific business errors live in each feature's own {@code *ErrorCode}. Every
 * error {@link ApiExceptionHandler} produces is coded; none is uncoded. Wire strings are immutable once
 * shipped, and {@code ErrorCatalogSnapshotTest} turns any change into a reviewable diff.
 */
public enum ApiErrorCode implements WireError {
    MALFORMED_BODY("validation.malformed-body", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("validation.failed", HttpStatus.BAD_REQUEST),
    NUMBER_NOT_STRING("money.number-not-string", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("validation.bad-request", HttpStatus.BAD_REQUEST),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("request.method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("request.not-acceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("request.unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    SERVICE_UNAVAILABLE("request.service-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL("platform.internal", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String wire;
    private final HttpStatus status;

    ApiErrorCode(String wire, HttpStatus status) {
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
