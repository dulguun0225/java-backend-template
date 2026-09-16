package com.example.starter.platform;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * The single keyset-pagination helper: no offset, no totals by default, and the only sanctioned place an
 * id column appears in an {@code ORDER BY}, as the final tiebreak after a business sort key and never as
 * the sole sort. Pages are descending over a {@code (timestamp, uuid)} key; the cursor is an opaque
 * base64url token encoding that key's exact position, so a tampered or malformed cursor fails loud
 * ({@link InvalidCursorException}) rather than mis-seeking. Stateless; the caller supplies the jOOQ fields
 * and the fetched {@code pageSize + 1} rows.
 */
public final class KeysetPager {

    private KeysetPager() {}

    /** A decoded seek position: the last row's sort value plus its id tiebreak. */
    public record Cursor(OffsetDateTime sortValue, UUID id) {}

    /** One page: the items (at most {@code pageSize}) and the cursor to the next page, or null at the end. */
    public record Page<T>(List<T> items, @Nullable String nextCursor) {}

    public static String encode(OffsetDateTime sortValue, UUID id) {
        String raw = sortValue + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decode a client cursor; empty for the first page (null or blank), throws on tampered input. */
    public static Optional<Cursor> decode(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException();
            }
            OffsetDateTime sortValue = OffsetDateTime.parse(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return Optional.of(new Cursor(sortValue, id));
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw new InvalidCursorException();
        }
    }

    /** The seek predicate for the next descending page past {@code cursor}; a no-op for the first page. */
    public static Condition seekDescending(
            Field<OffsetDateTime> sortField, Field<UUID> idField, @Nullable Cursor cursor) {
        if (cursor == null) {
            return DSL.noCondition();
        }
        return sortField
                .lt(cursor.sortValue())
                .or(sortField.eq(cursor.sortValue()).and(idField.lt(cursor.id())));
    }

    /** The descending order: business sort key first, id as the final tiebreak. */
    public static List<OrderField<?>> orderDescending(Field<OffsetDateTime> sortField, Field<UUID> idField) {
        return List.of(sortField.desc(), idField.desc());
    }

    /**
     * Assemble a page from {@code pageSize + 1} fetched rows: the extra row, if present, signals a next page
     * and supplies its cursor; it is dropped from the returned items.
     */
    public static <T> Page<T> toPage(List<T> fetched, int pageSize, Function<T, String> cursorOf) {
        if (fetched.size() <= pageSize) {
            return new Page<>(List.copyOf(fetched), null);
        }
        List<T> items = List.copyOf(fetched.subList(0, pageSize));
        return new Page<>(items, cursorOf.apply(items.get(items.size() - 1)));
    }
}
