package com.example.starter.platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Money value object: an exact decimal amount in one ISO 4217 currency, always carried at the currency's
 * minor-unit scale. All money in the system flows through this type. Raw {@link BigDecimal} arithmetic on
 * amounts is banned outside the platform tier by
 * {@code BanListArchTest.noRawBigDecimalArithmeticOutsidePlatform} (an owner-typed rule: construction
 * and inspection such as {@code scale()} or {@code signum()} stay allowed). Inside the platform tier,
 * bytecode cannot tell money arithmetic from rate arithmetic, so that slice rests on this type being the
 * only money-arithmetic surface; {@code BanCoverageMetaTest} records that as a declared, asserted tier.
 *
 * <p>Same-currency {@link #plus} and {@link #minus} are exact. Cross-currency arithmetic fails loud.
 * Excess input precision is rejected, never silently rounded: every rounding decision names its
 * {@link RoundingMode} at the call site, usually resolved through a {@link RoundingPolicy}.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        int minorUnits = currency.getDefaultFractionDigits();
        if (minorUnits < 0) {
            throw new IllegalArgumentException("currency has no minor units: " + currency.getCurrencyCode());
        }
        // Normalise to the currency's scale; UNNECESSARY rejects (does not round) excess precision.
        amount = amount.setScale(minorUnits, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /** Exact integer scaling; no rounding is possible, so none is named. */
    public Money times(long factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /**
     * {@code round(amount × numerator ÷ denominator)} to the currency's minor units with an explicit
     * {@link RoundingMode}. The intermediate product is exact and rounded once on the divide, so daily
     * interest {@code round(P × ratePercent ÷ (365 × 100))} matches the per-day contractual figure.
     */
    public Money timesRatio(BigDecimal numerator, long denominator, RoundingMode rounding) {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator must be non-zero");
        }
        BigDecimal scaled = amount.multiply(numerator)
                .divide(BigDecimal.valueOf(denominator), currency.getDefaultFractionDigits(), rounding);
        return new Money(scaled, currency);
    }

    /** {@code round(amount × factor)} to the currency's minor units with an explicit {@link RoundingMode}. */
    public Money timesFactor(BigDecimal factor, RoundingMode rounding) {
        BigDecimal scaled = amount.multiply(factor).setScale(currency.getDefaultFractionDigits(), rounding);
        return new Money(scaled, currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
