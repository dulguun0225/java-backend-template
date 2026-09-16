package com.example.starter.platform;

/**
 * The closed set of occasions on which money is rounded. Every sub-minor-unit result names its occasion
 * and a {@link RoundingPolicy} maps the occasion to a {@code RoundingMode}, so the whole rounding surface
 * is one reviewable object and no call site rounds with an unnamed or silently defaulted mode.
 *
 * <p>Template seed: extend this enum as the domain acquires occasions. A {@link RoundingPolicy} must map
 * every constant, so adding one here fails construction of every policy that does not name it, which is
 * the point.
 */
public enum RoundingOccasion {
    /** A percentage or formula fee applied to an amount. */
    FEE_PERCENT,
    /** A tax computed on an amount. */
    TAX,
    /** A currency conversion. */
    FX_CONVERSION,
    /** Splitting an amount across parts (instalments, allocations, shares). */
    ALLOCATION
}
