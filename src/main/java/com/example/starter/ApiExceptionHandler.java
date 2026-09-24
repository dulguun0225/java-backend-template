package com.example.starter;

import com.example.starter.platform.DecimalNotStringException;
import com.example.starter.platform.Ids;
import com.example.starter.platform.error.Rejected;
import com.example.starter.platform.error.ValidationFailed;
import com.example.starter.platform.observability.Log;
import com.example.starter.platform.observability.LogContext;
import com.example.starter.platform.observability.LogEvent;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one explicit API exception handler: turns framework-edge failures into RFC 9457 problems carrying a
 * compile-checked {@link ApiErrorCode} wire {@code code}, so no error path is uncoded or leaks an exception
 * message. Extends {@link ResponseEntityExceptionHandler} so Spring's standard MVC exceptions keep their
 * status mappings; only a genuinely unexpected throwable reaches the 500 path.
 *
 * <p>Business rejections arrive as {@link Rejected} carrying the feature's own catalog code; this class only
 * renders them.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Log log = Log.forClass(ApiExceptionHandler.class);

    /**
     * A request body that is not a JSON object at all. A decimal sent as a JSON number is
     * {@code money.number-not-string}; everything else is {@code validation.malformed-body} with a caller-safe
     * {@code detail}: where the JSON stopped being well formed (line and column), that the body is not an
     * object, or that it is missing. Spring raises this exception with no cause for a required body that was
     * not sent, which is the missing case. A body that is an object never reaches here: every failure inside it
     * is a {@code validation.failed} entry naming the member ({@link StrictJsonBodyConverter}).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (hasCause(ex, DecimalNotStringException.class)) {
            return problem(ApiErrorCode.NUMBER_NOT_STRING);
        }
        UnreadableBody unreadable = UnreadableBody.in(ex);
        String detail =
                unreadable != null ? unreadable.detail() : new UnreadableBody(UnreadableBody.Kind.MISSING).detail();
        ProblemDetail body = ProblemDetail.forStatus(ApiErrorCode.MALFORMED_BODY.status());
        body.setProperty("code", ApiErrorCode.MALFORMED_BODY.wire());
        body.setDetail(detail);
        return ResponseEntity.status(ApiErrorCode.MALFORMED_BODY.status()).body(body);
    }

    @ExceptionHandler(DecimalNotStringException.class)
    ResponseEntity<Object> handleDecimalNotString(DecimalNotStringException ex) {
        return problem(ApiErrorCode.NUMBER_NOT_STRING);
    }

    /** A field-validation rejection: 400 {@code validation.failed} with the {@code errors} array. */
    @ExceptionHandler(ValidationFailed.class)
    ResponseEntity<Object> handleValidationFailed(ValidationFailed ex) {
        ProblemDetail body = ProblemDetail.forStatus(ApiErrorCode.VALIDATION_FAILED.status());
        body.setProperty("code", ApiErrorCode.VALIDATION_FAILED.wire());
        body.setProperty("errors", ex.errors());
        return ResponseEntity.status(ApiErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    /** A coded business rejection: the feature's own catalog code at the status that code declares. */
    @ExceptionHandler(Rejected.class)
    ResponseEntity<Object> handleRejected(Rejected ex) {
        ProblemDetail body = ProblemDetail.forStatus(ex.code().status());
        body.setProperty("code", ex.code().wire());
        return ResponseEntity.status(ex.code().status()).body(body);
    }

    /**
     * The funnel every base-class handler delegates to. Stamping the catalog {@code code} by status here codes
     * all the standard MVC failures at once. Server-side faults the base class surfaces are logged under
     * {@link LogEvent#REQUEST_UNHANDLED_ERROR} with a joinable incident id, like the catch-all 500.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (!hasCode(problem)) {
                problem.setProperty("code", codeFor(statusCode).wire());
            }
            if (statusCode.is5xxServerError()) {
                problem.setProperty("incidentId", logUnhandled(ex));
            }
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    private static ApiErrorCode codeFor(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ApiErrorCode.NOT_FOUND;
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ApiErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (status.isSameCodeAs(HttpStatus.NOT_ACCEPTABLE)) {
            return ApiErrorCode.NOT_ACCEPTABLE;
        }
        if (status.isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)) {
            return ApiErrorCode.SERVICE_UNAVAILABLE;
        }
        if (status.is5xxServerError()) {
            return ApiErrorCode.INTERNAL;
        }
        return ApiErrorCode.BAD_REQUEST;
    }

    /**
     * Last resort: an unexpected throwable becomes a coded 500 carrying only a traceable incident id. The
     * exception message is never put on the wire ({@code ApiErrorEdgeIT} pins this). The full cause is logged
     * through the facade, and the wire {@code incidentId} is the request's correlation id, so one id joins
     * the client's 500 to the server log line.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        body.setProperty("code", ApiErrorCode.INTERNAL.wire());
        body.setProperty("incidentId", logUnhandled(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static String logUnhandled(Exception ex) {
        log.event(LogEvent.REQUEST_UNHANDLED_ERROR, ex);
        String correlationId = LogContext.correlationId();
        return correlationId != null ? correlationId : Ids.newId().toString();
    }

    private static ResponseEntity<Object> problem(ApiErrorCode code) {
        ProblemDetail body = ProblemDetail.forStatus(code.status());
        body.setProperty("code", code.wire());
        return ResponseEntity.status(code.status()).body(body);
    }

    /** Walks the cause chain with a hop bound instead of a self-reference check, so a cyclic chain still terminates. */
    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        Throwable cause = ex;
        for (int hops = 0; cause != null && hops < 32; hops++) {
            if (type.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
