package com.example.starter.platform;

import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * A rounding policy: the single object that maps every {@link RoundingOccasion} to a {@code RoundingMode}.
 * Money rounding never picks a mode implicitly; a call site resolves it through
 * {@link #mode(RoundingOccasion)}. A policy must map every occasion, checked at construction, so there is
 * no silent default at lookup time. Immutable; overrides produce a new policy.
 */
public final class RoundingPolicy {

    private final Map<RoundingOccasion, RoundingMode> modes;

    private RoundingPolicy(Map<RoundingOccasion, RoundingMode> modes) {
        EnumMap<RoundingOccasion, RoundingMode> copy = new EnumMap<>(RoundingOccasion.class);
        copy.putAll(modes);
        EnumSet<RoundingOccasion> missing = EnumSet.allOf(RoundingOccasion.class);
        missing.removeAll(copy.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("rounding policy must map every occasion; missing " + missing);
        }
        this.modes = copy;
    }

    /** A policy from an explicit, complete occasion-to-mode map. */
    public static RoundingPolicy of(Map<RoundingOccasion, RoundingMode> modes) {
        return new RoundingPolicy(modes);
    }

    /** HALF_UP for every occasion; the usual commercial default, overridable per occasion. */
    public static RoundingPolicy halfUpEverywhere() {
        EnumMap<RoundingOccasion, RoundingMode> all = new EnumMap<>(RoundingOccasion.class);
        for (RoundingOccasion occasion : RoundingOccasion.values()) {
            all.put(occasion, RoundingMode.HALF_UP);
        }
        return new RoundingPolicy(all);
    }

    /** The rounding mode for an occasion; never null, because the policy is complete by construction. */
    public RoundingMode mode(RoundingOccasion occasion) {
        RoundingMode mode = modes.get(occasion);
        if (mode == null) {
            throw new IllegalStateException("no rounding mode for " + occasion);
        }
        return mode;
    }

    /** A new policy with one occasion's mode replaced. */
    public RoundingPolicy withOverride(RoundingOccasion occasion, RoundingMode mode) {
        EnumMap<RoundingOccasion, RoundingMode> overridden = new EnumMap<>(modes);
        overridden.put(occasion, mode);
        return new RoundingPolicy(overridden);
    }
}
