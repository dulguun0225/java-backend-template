package com.example.starter.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoundingPolicyTest {

    @Test
    void anIncompletePolicyFailsAtConstruction() {
        Map<RoundingOccasion, RoundingMode> partial = new EnumMap<>(RoundingOccasion.class);
        partial.put(RoundingOccasion.FEE_PERCENT, RoundingMode.HALF_EVEN);
        assertThatThrownBy(() -> RoundingPolicy.of(partial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void anOverrideChangesOneOccasionOnly() {
        RoundingPolicy policy =
                RoundingPolicy.halfUpEverywhere().withOverride(RoundingOccasion.TAX, RoundingMode.FLOOR);
        assertThat(policy.mode(RoundingOccasion.TAX)).isEqualTo(RoundingMode.FLOOR);
        assertThat(policy.mode(RoundingOccasion.FEE_PERCENT)).isEqualTo(RoundingMode.HALF_UP);
    }
}
