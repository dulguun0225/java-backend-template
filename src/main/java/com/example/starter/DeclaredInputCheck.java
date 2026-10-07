package com.example.starter;

import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.ValidationFailed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Refuses, before any argument is read, what Spring would otherwise resolve silently, each at the input's
 * {@code in} and {@code name} as the handler declares it, all of one request in one {@code validation.failed}:
 *
 * <ul>
 *   <li>a query parameter the operation does not declare, which Spring ignores: {@code validation.unknown-field},
 *       {@code allowed} the declared ones;
 *   <li>a query parameter given twice, of which Spring binds the first or joins both with a comma, and a header the
 *       operation declares single-valued given twice: {@code validation.duplicate-member};
 *   <li>the text of a path variable, query parameter or header declared as a UUID, an {@code int32}, an
 *       {@code int64} or an enumeration that is not that type's one exact form, empty and blank included:
 *       {@code validation.invalid-value} with the format {@code expected}, or {@code validation.unknown-value} with
 *       the values {@code allowed}. Spring's conversions trim, read {@code 0x10} and {@code #10} as hexadecimal and
 *       {@code 1 2} as {@code 12}, take {@code 1-1-1-1-1} as a UUID and an empty text as no value; and a converter
 *       registered in their place does not hold, because Spring retries a value its conversion service refused with
 *       its default property editors, which are the lenient ones. So the text is checked here, and what passes
 *       converts to the same value under Spring's own conversion.
 * </ul>
 *
 * <p>The query is read from {@link HttpServletRequest#getQueryString()}, each name and value percent-decoded as the
 * container decodes them, {@code +} as a space, and never from {@code getParameterNames()}, which merges a form
 * body's fields with the query and keeps one entry per name. A header the operation does not declare is never
 * refused: RFC 9110 §5.1 has a recipient ignore one, and proxies add them. {@code Accept}, {@code Content-Type} and
 * {@code Authorization} keep their own refusals and are not checked here. An absent input is left to Spring, whose
 * refusal the edge answers as {@code validation.required}.
 *
 * <p>An operation's declaration is read from its handler's parameters: each {@code @RequestParam}, and each
 * unannotated simple value, which Spring binds as one, is a declared query parameter; each {@code @RequestHeader} of
 * a type that is not a collection, an array or a map is a single-valued header. A handler with any other parameter —
 * a model attribute, a {@code @RequestParam} map, the raw request — may read the query by a name its declaration
 * does not show, so the names its query carries are not checked; its declared inputs still are, a repeat of a
 * single-valued one included. A multi-valued header's lines are not checked: one line may carry several values.
 */
final class DeclaredInputCheck implements HandlerInterceptor {

    private static final Set<String> OWN_REFUSALS = Set.of("accept", "content-type", "authorization");

    private final Map<Method, Declared> declarations = new ConcurrentHashMap<>();

    /** One declared input: where it travels, its declared name, the type each of its values binds to. */
    record Input(FieldError.In in, String name, Class<?> type, boolean multiValued) {}

    /**
     * What one handler declares: its inputs, and its query parameters, sorted, or none when it may read the query by
     * a name its declaration does not show.
     */
    record Declared(List<Input> inputs, Optional<List<String>> query) {

        static Declared of(HandlerMethod handler) {
            List<Input> inputs = new ArrayList<>();
            Set<String> query = new TreeSet<>();
            boolean closed = true;
            for (MethodParameter declared : handler.getMethodParameters()) {
                MethodParameter parameter = declared.nestedIfOptional();
                Class<?> type = parameter.getNestedParameterType();
                boolean multiValued = Collection.class.isAssignableFrom(type) || type.isArray();
                Class<?> valueType = multiValued ? elementType(parameter) : type;
                RequestParam param = declared.getParameterAnnotation(RequestParam.class);
                RequestHeader header = declared.getParameterAnnotation(RequestHeader.class);
                PathVariable path = declared.getParameterAnnotation(PathVariable.class);
                if (Map.class.isAssignableFrom(type) || HttpHeaders.class.isAssignableFrom(type)) {
                    if (header == null && path == null) {
                        closed = false;
                    }
                } else if (param != null) {
                    String name = nameOf(param.name(), param.value(), declared);
                    query.add(name);
                    inputs.add(new Input(FieldError.In.QUERY, name, valueType, multiValued));
                } else if (header != null) {
                    String name = nameOf(header.name(), header.value(), declared);
                    if (!OWN_REFUSALS.contains(name.toLowerCase(Locale.ROOT))) {
                        inputs.add(new Input(FieldError.In.HEADER, name, valueType, multiValued));
                    }
                } else if (path != null) {
                    inputs.add(new Input(FieldError.In.PATH, nameOf(path.name(), path.value(), declared), type, false));
                } else if (declared.getParameterAnnotations().length == 0 && BeanUtils.isSimpleProperty(type)) {
                    String name = nameOf("", "", declared);
                    query.add(name);
                    inputs.add(new Input(FieldError.In.QUERY, name, valueType, multiValued));
                } else if (!declared.hasParameterAnnotation(RequestBody.class)
                        && !declared.hasParameterAnnotation(CookieValue.class)) {
                    closed = false;
                }
            }
            return new Declared(List.copyOf(inputs), closed ? Optional.of(List.copyOf(query)) : Optional.empty());
        }

        private static Class<?> elementType(MethodParameter parameter) {
            Class<?> type = parameter.getNestedParameterType();
            if (type.isArray()) {
                return type.getComponentType();
            }
            Class<?> element =
                    ResolvableType.forMethodParameter(parameter).asCollection().resolveGeneric(0);
            return element != null ? element : Object.class;
        }

        private static String nameOf(String name, String value, MethodParameter parameter) {
            if (!name.isEmpty()) {
                return name;
            }
            if (!value.isEmpty()) {
                return value;
            }
            String declared = parameter.getParameterName();
            if (declared == null) {
                throw new IllegalStateException("compile with -parameters: " + parameter);
            }
            return declared;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Declared declared = declarations.computeIfAbsent(method.getMethod(), key -> Declared.of(method));
        Map<String, List<String>> query = query(request.getQueryString());
        List<FieldError> errors = new ArrayList<>();
        declared.query().ifPresent(allowed -> errors.addAll(undeclared(query, allowed)));
        for (Input input : declared.inputs()) {
            List<String> values = values(input, request, query);
            if (input.in() != FieldError.In.PATH && !input.multiValued() && values.size() > 1) {
                errors.add(FieldError.ofParameter(input.in(), input.name(), new ApiFieldCode.DuplicateMember()));
                continue;
            }
            FieldError refused = refused(input, values);
            if (refused != null) {
                errors.add(refused);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailed(errors);
        }
        return true;
    }

    /**
     * Each name the query carries that {@code allowed} does not hold, once, and again as a duplicate when it is
     * carried twice, in the order each first appears.
     */
    static List<FieldError> undeclared(Map<String, List<String>> query, List<String> allowed) {
        List<FieldError> errors = new ArrayList<>();
        for (Map.Entry<String, List<String>> name : query.entrySet()) {
            if (allowed.contains(name.getKey())) {
                continue;
            }
            errors.add(
                    FieldError.ofParameter(FieldError.In.QUERY, name.getKey(), new ApiFieldCode.UnknownField(allowed)));
            if (name.getValue().size() > 1) {
                errors.add(
                        FieldError.ofParameter(FieldError.In.QUERY, name.getKey(), new ApiFieldCode.DuplicateMember()));
            }
        }
        return errors;
    }

    /** The query string's values by name, names in the order each first appears, each decoded. */
    static Map<String, List<String>> query(@Nullable String queryString) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return query;
        }
        for (String pair : queryString.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            query.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return query;
    }

    /** The texts sent for one input: the path variable's, the query's for its name, or every line of the header. */
    private static List<String> values(Input input, HttpServletRequest request, Map<String, List<String>> query) {
        return switch (input.in()) {
            case PATH -> {
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Object value = variables instanceof Map<?, ?> map ? map.get(input.name()) : null;
                yield value == null ? List.of() : List.of(String.valueOf(value));
            }
            case QUERY -> query.getOrDefault(input.name(), List.of());
            case HEADER -> {
                Enumeration<String> lines = request.getHeaders(input.name());
                yield lines == null ? List.of() : Collections.list(lines);
            }
        };
    }

    /**
     * The refusal of the first value that is not its type's exact form, or {@code null} when every one is, or the
     * type is not one whose form is checked here.
     */
    private static @Nullable FieldError refused(Input input, List<String> values) {
        if (input.multiValued() && input.in() == FieldError.In.HEADER) {
            return null;
        }
        for (String text : values) {
            if (input.type().isEnum()) {
                List<String> allowed = InputTypes.enumValues(input.type());
                if (!allowed.contains(text)) {
                    return FieldError.ofParameter(input.in(), input.name(), new ApiFieldCode.UnknownValue(allowed));
                }
            } else if (!exactForm(input.type(), text)) {
                String expected = InputTypes.expected(input.type());
                return FieldError.ofParameter(
                        input.in(), input.name(), new ApiFieldCode.InvalidValue(expected), "expected " + expected);
            }
        }
        return null;
    }

    /** Whether {@code text} is the one form of a UUID, an {@code int32} or an {@code int64}; any other type passes. */
    private static boolean exactForm(Class<?> type, String text) {
        try {
            if (type == UUID.class) {
                return InputTypes.uuid(text) != null;
            }
            if (type == Integer.class || type == int.class) {
                Integer.parseInt(text);
            } else if (type == Long.class || type == long.class) {
                Long.parseLong(text);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** A name or value as the container decodes it, {@code +} as a space; one whose escapes do not decode, as sent. */
    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }
}
