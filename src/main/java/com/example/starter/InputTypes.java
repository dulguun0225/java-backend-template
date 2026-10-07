package com.example.starter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a refusal says a request input must be, from the Java type it binds to, in the document's own words: the
 * schema {@code format} springdoc writes for the type, else its JSON {@code type}. One table for the body reader
 * and the edge, so a member and a parameter of one type are refused naming the same thing.
 */
final class InputTypes {

    /** RFC 9562's one string form of a UUID: 8-4-4-4-12 hexadecimal digits, either case, nothing around it. */
    private static final Pattern UUID_FORM =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Map<Class<?>, String> FORMATS = Map.ofEntries(
            Map.entry(UUID.class, "uuid"),
            Map.entry(Integer.class, "int32"),
            Map.entry(int.class, "int32"),
            Map.entry(Long.class, "int64"),
            Map.entry(long.class, "int64"),
            Map.entry(LocalDate.class, "date"),
            Map.entry(Instant.class, "date-time"),
            Map.entry(OffsetDateTime.class, "date-time"),
            Map.entry(ZonedDateTime.class, "date-time"));

    private InputTypes() {}

    /** The schema {@code format} of {@code type}, or {@code null} when its schema has none. */
    static @Nullable String format(Class<?> type) {
        return FORMATS.get(type);
    }

    /** The JSON type of {@code type}'s schema: {@code string}, {@code boolean}, {@code integer}, and so on. */
    static String jsonType(Class<?> type) {
        if (CharSequence.class.isAssignableFrom(type)
                || type.isEnum()
                || type == Character.class
                || type == char.class
                || type == UUID.class
                || type.getPackageName().equals("java.time")) {
            return "string";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        if (type == Integer.class
                || type == int.class
                || type == Long.class
                || type == long.class
                || type == Short.class
                || type == short.class
                || type == Byte.class
                || type == byte.class
                || type == BigInteger.class) {
            return "integer";
        }
        if (Number.class.isAssignableFrom(type)
                || type == double.class
                || type == float.class
                || type == BigDecimal.class) {
            return "number";
        }
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return "array";
        }
        return "object";
    }

    /** The {@code expected} param of {@code validation.invalid-value}: the schema's format, else its type. */
    static String expected(Class<?> type) {
        String format = format(type);
        return format != null ? format : jsonType(type);
    }

    /** An enumeration's values as a query parameter or a body member spells them, sorted. */
    static List<String> enumValues(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        if (constants == null) {
            return List.of();
        }
        return Arrays.stream(constants)
                .map(constant -> ((Enum<?>) constant).name())
                .sorted()
                .toList();
    }

    /** {@code text} as a UUID when it is the 36-character form exactly, untrimmed; otherwise {@code null}. */
    static @Nullable UUID uuid(String text) {
        return UUID_FORM.matcher(text).matches() ? UUID.fromString(text) : null;
    }
}
