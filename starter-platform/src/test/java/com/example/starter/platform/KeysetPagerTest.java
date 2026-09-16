package com.example.starter.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeysetPagerTest {

    @Test
    void cursorRoundTrips() {
        OffsetDateTime at = OffsetDateTime.of(2026, 9, 16, 12, 0, 0, 0, ZoneOffset.UTC);
        UUID id = Ids.newId();
        KeysetPager.Cursor decoded =
                KeysetPager.decode(KeysetPager.encode(at, id)).orElseThrow();
        assertThat(decoded.sortValue()).isEqualTo(at);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void aTamperedCursorFailsLoud() {
        assertThatThrownBy(() -> KeysetPager.decode("bm90LWEtY3Vyc29y")).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void theExtraRowBecomesTheNextCursorAndIsDropped() {
        KeysetPager.Page<String> page = KeysetPager.toPage(List.of("a", "b", "c"), 2, s -> "cursor-" + s);
        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.nextCursor()).isEqualTo("cursor-b");
        assertThat(KeysetPager.toPage(List.of("a", "b"), 2, s -> s).nextCursor())
                .isNull();
    }
}
