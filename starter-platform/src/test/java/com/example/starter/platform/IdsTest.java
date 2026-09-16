package com.example.starter.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The bit-layout golden for the id producer: {@link Ids#newId()} must be a UUIDv7, never a v4. */
class IdsTest {

    @Test
    void newIdIsUuidVersion7IetfVariant() {
        UUID id = Ids.newId();
        assertThat(id.version()).as("UUID version").isEqualTo(7);
        assertThat(id.variant()).as("IETF variant (10xx)").isEqualTo(2);
    }

    @Test
    void newIdsAreUnique() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(ids.add(Ids.newId())).as("duplicate id at %d", i).isTrue();
        }
    }

    @Test
    void consecutiveIdsAreTimeOrdered() {
        UUID first = Ids.newId();
        UUID second = Ids.newId();
        // The top 48 bits are the millisecond timestamp, so a later id never sorts before an earlier one.
        assertThat(second.getMostSignificantBits() >>> 16)
                .isGreaterThanOrEqualTo(first.getMostSignificantBits() >>> 16);
    }
}
