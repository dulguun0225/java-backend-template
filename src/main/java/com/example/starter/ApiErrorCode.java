package com.example.starter;

import com.example.starter.platform.error.ProblemParams;
import com.example.starter.platform.error.WireError;
import org.springframework.http.HttpStatus;

/**
 * The compile-checked catalog for cross-cutting API errors raised below any single feature: a malformed
 * body, a decimal sent as a JSON number, a request body over the size limit ({@code request.too-large}, the one
 * 413 the service sends), the standard MVC 4xx, a field-validation rejection, and the last-resort 500.
 * Feature-specific business errors live in each feature's own {@code *ErrorCode}. Every error
 * {@link ApiExceptionHandler} produces is coded; none is uncoded. A code that carries params names its
 * {@link ProblemParams} record, nested here. Wire strings and param names are immutable once shipped, and
 * {@code ErrorCatalogSnapshotTest} turns any change into a reviewable diff.
 */
public enum ApiErrorCode implements WireError {
    MALFORMED_BODY("validation.malformed-body", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("validation.failed", HttpStatus.BAD_REQUEST),
    NUMBER_NOT_STRING("money.number-not-string", HttpStatus.BAD_REQUEST),

    /** A framework 400 no input code describes; a request input that does not convert is a field entry instead. */
    BAD_REQUEST("validation.bad-request", HttpStatus.BAD_REQUEST),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("request.method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("request.not-acceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("request.unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** A request body over the configured limit, refused before any member is read; {@code max} is the limit. */
    TOO_LARGE("request.too-large", HttpStatus.CONTENT_TOO_LARGE, TooLarge.class),
    SERVICE_UNAVAILABLE("request.service-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL("platform.internal", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String wire;
    private final HttpStatus status;
    private final Class<? extends Record> paramsType;

    ApiErrorCode(String wire, HttpStatus status) {
        this(wire, status, NoParams.class);
    }

    ApiErrorCode(String wire, HttpStatus status, Class<? extends Record> paramsType) {
        this.wire = wire;
        this.status = status;
        this.paramsType = paramsType;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Override
    public int status() {
        return status.value();
    }

    @Override
    public Class<? extends Record> paramsType() {
        return paramsType;
    }

    /** {@code request.too-large}: {@code max} is the largest request body accepted, in bytes. */
    public record TooLarge(long max) implements ProblemParams {
        @Override
        public WireError code() {
            return TOO_LARGE;
        }
    }
}
