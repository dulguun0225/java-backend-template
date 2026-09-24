package com.example.starter;

import com.example.starter.platform.DecimalNotStringException;
import com.example.starter.platform.error.BoundBody;
import com.example.starter.platform.error.FieldError;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one reader of request bodies: binds a {@code @RequestBody BoundBody<T>} parameter strictly, recording
 * every member the record {@code T} does not declare, every path identifier sent in the body and every value
 * of the wrong JSON type, all in one streaming pass, so one {@code validation.failed} can name all of them
 * together with the feature's own field-rule failures ({@link BoundBody#validate}).
 *
 * <p>The mechanism is a per-read Jackson {@link DeserializationProblemHandler} on Boot's own mapper, which
 * leaves every mapper-wide setting as it is — the scalar coercions, the numeric binding of {@code Number}
 * fields — and changes only what happens at the points Jackson would otherwise drop or fail: an unknown
 * member is recorded and skipped, a wrong-typed value is recorded and bound as absent. Rejected alternatives,
 * recorded in {@code docs/GATES.md}: a {@code JsonNode} tree diff (the tree keeps one of two equal keys, so a
 * member-level deserializer could no longer see a duplicated key and refuse it); a {@code @JsonAnySetter} per
 * request record (one copy per record, and nothing checks that every record has it); {@code RequestBodyAdvice}
 * or a customised reader (neither can hand the collected failures out of {@code read});
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} on the mapper (first failure only, and mapper-wide: the same mapper decodes
 * broker messages once a handoff exists, and those must tolerate a member a newer producer added); Jakarta Bean Validation (a second
 * validation mechanism beside the services' rules).
 *
 * <p>A path identifier is recognised without any per-record declaration: an unknown top-level member whose
 * name is one of the matched route's URI template variables is {@code validation.identifier-in-path}. Spring
 * sets that attribute when it matches the handler, before any argument is resolved.
 *
 * <p>Input that is not a JSON object at all — nothing, a top-level array or scalar, text that is not
 * well-formed JSON, trailing content after the object — has no member to name and is raised as
 * {@link HttpMessageNotReadableException} with an {@link UnreadableBody} cause, which
 * {@link ApiExceptionHandler} renders as {@code validation.malformed-body} with a {@code detail} giving the
 * line and column. A JSON number where a string decimal is expected is rethrown with its
 * {@link DecimalNotStringException} cause, so {@code money.number-not-string} still answers it. No
 * {@code detail} and no exception text is ever built from what the caller sent.
 */
final class StrictJsonBodyConverter implements SmartHttpMessageConverter<Object> {

    private static final String UNREADABLE = "request body unreadable";
    private static final List<MediaType> MEDIA_TYPES =
            List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));

    private final JsonMapper mapper;

    StrictJsonBodyConverter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean canRead(ResolvableType type, @Nullable MediaType mediaType) {
        return type.toClass() == BoundBody.class && (mediaType == null || isJson(mediaType));
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, @Nullable MediaType mediaType) {
        return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return MEDIA_TYPES;
    }

    @Override
    public Object read(ResolvableType type, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints)
            throws IOException {
        JavaType target =
                mapper.constructType(type.as(BoundBody.class).getGeneric(0).getType());
        Collector collector = new Collector(pathVariables());
        ObjectReader reader =
                mapper.readerFor(target).withHandler(collector).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream body = inputMessage.getBody();
                JsonParser parser = reader.createParser(body)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw unreadable(new UnreadableBody(UnreadableBody.Kind.MISSING), inputMessage);
            }
            if (first != JsonToken.START_OBJECT) {
                throw unreadable(new UnreadableBody(UnreadableBody.Kind.NOT_AN_OBJECT), inputMessage);
            }
            Object value = reader.readValue(parser);
            if (parser.nextToken() != null) {
                throw unreadable(notWellFormed(parser.currentTokenLocation()), inputMessage);
            }
            if (value == null) {
                if (collector.errors.isEmpty()) {
                    throw unreadable(new UnreadableBody(UnreadableBody.Kind.NOT_AN_OBJECT), inputMessage);
                }
                return BoundBody.unbound(collector.errors);
            }
            return BoundBody.of(value, collector.errors);
        } catch (StreamReadException | StreamConstraintsException e) {
            throw unreadable(notWellFormed(e.getLocation()), inputMessage);
        } catch (DatabindException e) {
            if (hasCause(e, DecimalNotStringException.class)) {
                throw new HttpMessageNotReadableException(UNREADABLE, e, inputMessage);
            }
            String pointer = pointerOf(e.getPath());
            if (pointer.isEmpty()) {
                throw unreadable(notWellFormed(e.getLocation()), inputMessage);
            }
            List<FieldError> errors = new ArrayList<>(collector.errors);
            errors.add(FieldError.of(pointer, ApiFieldCode.WRONG_TYPE, expected(e)));
            return BoundBody.unbound(errors);
        }
    }

    @Override
    public void write(
            Object value,
            ResolvableType type,
            @Nullable MediaType contentType,
            HttpOutputMessage outputMessage,
            @Nullable Map<String, Object> hints)
            throws HttpMessageNotWritableException {
        throw new HttpMessageNotWritableException("this converter only reads request bodies");
    }

    private static boolean isJson(MediaType mediaType) {
        return MEDIA_TYPES.stream().anyMatch(json -> json.includes(mediaType));
    }

    /** The matched route's URI template variable names; set by Spring before any argument is resolved. */
    private static Set<String> pathVariables() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Set.of();
        }
        Object variables = attributes.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (variables instanceof Map<?, ?> map) {
            Set<String> names = new java.util.HashSet<>();
            map.keySet().forEach(key -> names.add(String.valueOf(key)));
            return Set.copyOf(names);
        }
        return Set.of();
    }

    private static UnreadableBody notWellFormed(@Nullable TokenStreamLocation location) {
        if (location == null || location.getLineNr() < 1 || location.getColumnNr() < 1) {
            return new UnreadableBody(UnreadableBody.Kind.NOT_WELL_FORMED);
        }
        return new UnreadableBody(UnreadableBody.Kind.NOT_WELL_FORMED, location.getLineNr(), location.getColumnNr());
    }

    private static HttpMessageNotReadableException unreadable(UnreadableBody cause, HttpInputMessage inputMessage) {
        return new HttpMessageNotReadableException(UNREADABLE, cause, inputMessage);
    }

    /** The RFC 6901 pointer of a Jackson reference path, each segment escaped ({@code ~0}, {@code ~1}). */
    static String pointerOf(List<JacksonException.Reference> path) {
        StringBuilder pointer = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            String name = reference.getPropertyName();
            if (name != null) {
                pointer.append('/').append(escape(name));
            } else if (reference.getIndex() >= 0) {
                pointer.append('/').append(reference.getIndex());
            }
        }
        return pointer.toString();
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static String expected(DatabindException e) {
        if (e instanceof MismatchedInputException mismatch && mismatch.getTargetType() != null) {
            return expected(mismatch.getTargetType());
        }
        return "expected object";
    }

    /** The expected JSON type of a bound Java type, named in {@code detail} as {@code expected <type>}. */
    static String expected(Class<?> type) {
        if (CharSequence.class.isAssignableFrom(type) || type.isEnum() || type == Character.class) {
            return "expected string";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "expected boolean";
        }
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != void.class)) {
            return "expected integer";
        }
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return "expected array";
        }
        return "expected object";
    }

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

    /**
     * Collects the binding failures of one read. Each callback records the member's pointer, taken from the
     * parser's own read context (RFC 6901, escaped), and a fixed expected-type text — never the value or
     * Jackson's message, which quotes it.
     */
    static final class Collector extends DeserializationProblemHandler {

        private final Set<String> pathVariables;
        final List<FieldError> errors = new ArrayList<>();

        Collector(Set<String> pathVariables) {
            this.pathVariables = pathVariables;
        }

        @Override
        public boolean handleUnknownProperty(
                DeserializationContext context,
                JsonParser parser,
                ValueDeserializer<?> deserializer,
                Object beanOrClass,
                String propertyName) {
            TokenStreamContext read = parser.streamReadContext();
            String pointer = pointer(parser);
            TokenStreamContext parent = read.getParent();
            boolean topLevel = parent != null && parent.inRoot();
            errors.add(
                    topLevel && pathVariables.contains(propertyName)
                            ? FieldError.of(pointer, ApiFieldCode.IDENTIFIER_IN_PATH)
                            : FieldError.of(pointer, ApiFieldCode.UNKNOWN_FIELD));
            parser.skipChildren();
            return true;
        }

        @Override
        public @Nullable Object handleWeirdStringValue(
                DeserializationContext context, Class<?> targetType, String valueToConvert, String failureMsg) {
            wrongType(context.getParser(), expected(targetType));
            return null;
        }

        @Override
        public @Nullable Object handleWeirdNumberValue(
                DeserializationContext context, Class<?> targetType, Number valueToConvert, String failureMsg) {
            wrongType(context.getParser(), expected(targetType));
            return null;
        }

        @Override
        public @Nullable Object handleUnexpectedToken(
                DeserializationContext context,
                JavaType targetType,
                JsonToken token,
                JsonParser parser,
                String failureMsg) {
            wrongType(parser, expected(targetType.getRawClass()));
            parser.skipChildren();
            return null;
        }

        @Override
        public @Nullable Object handleMissingInstantiator(
                DeserializationContext context,
                Class<?> instClass,
                ValueInstantiator instantiator,
                JsonParser parser,
                String failureMsg) {
            wrongType(parser, expected(instClass));
            parser.skipChildren();
            return null;
        }

        private void wrongType(JsonParser parser, String detail) {
            String pointer = pointer(parser);
            if (pointer.isEmpty()) {
                return;
            }
            errors.add(FieldError.of(pointer, ApiFieldCode.WRONG_TYPE, detail));
        }

        private static String pointer(JsonParser parser) {
            return parser.streamReadContext().pathAsPointer().toString();
        }
    }
}
