package com.example.starter;

import com.example.starter.platform.DecimalNotStringException;
import com.example.starter.platform.Ids;
import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.ProblemParams;
import com.example.starter.platform.error.Rejected;
import com.example.starter.platform.error.ValidationFailed;
import com.example.starter.platform.error.WireError;
import com.example.starter.platform.observability.Log;
import com.example.starter.platform.observability.LogContext;
import com.example.starter.platform.observability.LogEvent;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * A path variable, query parameter or header whose text does not convert to its declared type — {@code abc}
     * where {@code /api/greetings/{id}} expects a UUID: a {@code validation.failed} entry at its {@code in} and
     * {@code name}, the name the handler declares. An enumeration's value outside its set is
     * {@code validation.unknown-value} with the values {@code allowed}; any other text, empty and blank included,
     * is {@code validation.invalid-value} with the format or type {@code expected}. Spring's own {@code detail}
     * quotes the value sent; nothing here is built from it. A mismatch on no handler parameter is
     * {@code validation.bad-request}.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            FieldError.In in = locationOf(mismatch.getParameter());
            if (in != null) {
                Class<?> required = mismatch.getRequiredType();
                Class<?> type =
                        required != null ? required : mismatch.getParameter().getNestedParameterType();
                return handleValidationFailed(new ValidationFailed(List.of(refused(in, mismatch.getName(), type))));
            }
        }
        ProblemDetail body = ProblemDetail.forStatus(ApiErrorCode.BAD_REQUEST.status());
        body.setProperty("code", ApiErrorCode.BAD_REQUEST.wire());
        body.setDetail("A request parameter has a value of the wrong type.");
        return ResponseEntity.status(ApiErrorCode.BAD_REQUEST.status()).body(body);
    }

    /**
     * A required query parameter whose name is not in the request: {@code validation.required}. One whose text
     * Spring's own conversion turns into no value — an empty {@code Boolean} — is
     * {@code validation.invalid-value}: the name was sent.
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return missing(
                FieldError.In.QUERY, ex.getParameterName(), ex.getMethodParameter(), ex.isMissingAfterConversion());
    }

    /**
     * A required header whose name is not in the request: {@code validation.required} at the name the handler
     * declares. As for a query parameter, one sent and converted to no value is {@code validation.invalid-value}.
     * Every other binding failure keeps Spring's handling, coded {@code validation.bad-request}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException header) {
            return missing(
                    FieldError.In.HEADER,
                    header.getHeaderName(),
                    header.getParameter(),
                    header.isMissingAfterConversion());
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    /**
     * A path variable sent and converted to no value is {@code validation.invalid-value}. One the route does not
     * carry at all is a handler declaring a variable its mapping lacks, a server fault, and stays Spring's 500.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMissingPathVariable(
            MissingPathVariableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.isMissingAfterConversion()) {
            return missing(FieldError.In.PATH, ex.getVariableName(), ex.getParameter(), true);
        }
        return super.handleMissingPathVariable(ex, headers, status, request);
    }

    private ResponseEntity<Object> missing(
            FieldError.In in, String name, @Nullable MethodParameter parameter, boolean sent) {
        FieldError error = sent
                ? refused(in, name, parameter == null ? String.class : parameter.getNestedParameterType())
                : FieldError.ofParameter(in, name, new ApiFieldCode.Required());
        return handleValidationFailed(new ValidationFailed(List.of(error)));
    }

    /** The entry for a text that is no value of {@code type}: outside its set, or not parsing as it. */
    private static FieldError refused(FieldError.In in, String name, Class<?> type) {
        if (type.isEnum()) {
            return FieldError.ofParameter(in, name, new ApiFieldCode.UnknownValue(InputTypes.enumValues(type)));
        }
        String expected = InputTypes.expected(type);
        return FieldError.ofParameter(in, name, new ApiFieldCode.InvalidValue(expected), "expected " + expected);
    }

    /**
     * Where a handler parameter is read from: its annotation, or the query for an unannotated simple value, which
     * Spring binds as a query parameter of the same name; {@code null} for any other parameter.
     */
    private static FieldError.@Nullable In locationOf(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(PathVariable.class)) {
            return FieldError.In.PATH;
        }
        if (parameter.hasParameterAnnotation(RequestHeader.class)) {
            return FieldError.In.HEADER;
        }
        if (parameter.hasParameterAnnotation(RequestParam.class)
                || (parameter.getParameterAnnotations().length == 0
                        && BeanUtils.isSimpleProperty(parameter.getNestedParameterType()))) {
            return FieldError.In.QUERY;
        }
        return null;
    }

    /**
     * A request body over the configured limit: 413 {@code request.too-large}, the limit in bytes as
     * {@code max}. The only 413 the service sends; any other is the catch-all 500.
     */
    @ExceptionHandler(RequestBodyTooLarge.class)
    ResponseEntity<Object> handleBodyTooLarge(RequestBodyTooLarge ex) {
        return problem(new ApiErrorCode.TooLarge(ex.max()), "The request body must be at most " + ex.max() + " bytes.");
    }

    @ExceptionHandler(DecimalNotStringException.class)
    ResponseEntity<Object> handleDecimalNotString(DecimalNotStringException ex) {
        return problem(ApiErrorCode.NUMBER_NOT_STRING);
    }

    /**
     * A field-validation rejection: 400 {@code validation.failed} with the {@code errors} array, and
     * {@code errorsOmitted}, the failures found past {@link ValidationFailed#MAX_ERRORS}, when there are any.
     */
    @ExceptionHandler(ValidationFailed.class)
    ResponseEntity<Object> handleValidationFailed(ValidationFailed ex) {
        ProblemDetail body = ProblemDetail.forStatus(ApiErrorCode.VALIDATION_FAILED.status());
        body.setProperty("code", ApiErrorCode.VALIDATION_FAILED.wire());
        body.setProperty("errors", ex.errors());
        if (ex.omitted() > 0) {
            body.setProperty("errorsOmitted", ex.omitted());
        }
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
     * {@link LogEvent#REQUEST_UNHANDLED_ERROR} with a joinable incident id, like the catch-all 500. A client-error
     * status no catalog code carries — a {@code ResponseStatusException(CONFLICT)} thrown by a feature — is a
     * missing catalog entry, a server fault: it is answered and logged as the catch-all 500, never sent under a
     * code whose catalog status is another one. A 413 from anywhere but the body limit is one of those: it could
     * not say the {@code max} every {@code request.too-large} carries.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (!hasCode(problem)) {
                ApiErrorCode code = codeFor(statusCode);
                if (code == null) {
                    return handleUnexpected(ex);
                }
                problem.setProperty("code", code.wire());
            }
            String allowed = allowedDetail(ex);
            if (allowed != null) {
                problem.setDetail(allowed);
            }
            if (statusCode.is5xxServerError()) {
                problem.setProperty("incidentId", logUnhandled(ex));
            }
        }
        return response;
    }

    /**
     * What is allowed, for the two refusals whose Spring {@code detail} quotes what the caller sent — the method
     * ({@code Method 'X' is not supported.}) and the {@code Content-Type} — replaced by the methods or media types
     * the route takes, which are the service's own values; {@code null} for every other exception.
     */
    static @Nullable String allowedDetail(Exception ex) {
        if (ex instanceof HttpRequestMethodNotSupportedException method) {
            String[] supported = method.getSupportedMethods();
            return supported == null || supported.length == 0
                    ? "The method is not supported here."
                    : "Supported methods: " + String.join(", ", supported) + ".";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException media) {
            return media.getSupportedMediaTypes().isEmpty()
                    ? "The content type is not supported here."
                    : "Supported content types: "
                            + String.join(
                                    ", ",
                                    media.getSupportedMediaTypes().stream()
                                            .map(Object::toString)
                                            .toList())
                            + ".";
        }
        return null;
    }

    private static boolean hasCode(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    /** The catalog code whose status is {@code status}, or {@code null} when no code carries it. */
    static @Nullable ApiErrorCode codeFor(HttpStatusCode status) {
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
        if (status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return ApiErrorCode.BAD_REQUEST;
        }
        if (status.is5xxServerError()) {
            return ApiErrorCode.INTERNAL;
        }
        return null;
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

    /** A problem whose code carries params: the code from the record, the record as {@code params}. */
    private static ResponseEntity<Object> problem(ProblemParams params, String detail) {
        WireError code = params.code();
        ProblemDetail body = ProblemDetail.forStatus(code.status());
        body.setProperty("code", code.wire());
        body.setProperty("params", params);
        body.setDetail(detail);
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
