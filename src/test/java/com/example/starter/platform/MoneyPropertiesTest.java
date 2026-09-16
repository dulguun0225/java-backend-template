package com.example.starter.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Financial-math invariants for {@link Money} as jqwik properties. Named {@code *Test} so surefire discovers
 * it and routes the {@code @Property} methods to the jqwik engine.
 */
class MoneyPropertiesTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Property
    void additionIsCommutative(@ForAll("usdAmounts") BigDecimal a, @ForAll("usdAmounts") BigDecimal b) {
        assertThat(Money.of(a, USD).plus(Money.of(b, USD)))
                .isEqualTo(Money.of(b, USD).plus(Money.of(a, USD)));
    }

    @Property
    void plusThenMinusRestoresTheOriginal(@ForAll("usdAmounts") BigDecimal a, @ForAll("usdAmounts") BigDecimal b) {
        Money x = Money.of(a, USD);
        assertThat(x.plus(Money.of(b, USD)).minus(Money.of(b, USD))).isEqualTo(x);
    }

    @Property
    void amountIsAlwaysCarriedAtTheCurrencyMinorUnits(@ForAll("usdAmounts") BigDecimal a) {
        assertThat(Money.of(a, USD).amount().scale()).isEqualTo(2);
    }

    @Property
    void timesRatioRoundsOnceToMinorUnits(@ForAll("usdAmounts") BigDecimal a) {
        Money result = Money.of(a, USD).timesRatio(new BigDecimal("7"), 365, RoundingMode.HALF_UP);
        assertThat(result.amount().scale()).isEqualTo(2);
    }

    @Example
    void crossCurrencyArithmeticFailsLoud() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("10.00", "EUR");
        assertThatThrownBy(() -> usd.plus(eur)).isInstanceOf(IllegalArgumentException.class);
    }

    @Example
    void excessPrecisionIsRejectedNotRounded() {
        assertThatThrownBy(() -> Money.of("10.005", "USD")).isInstanceOf(ArithmeticException.class);
    }

    @Provide
    Arbitrary<BigDecimal> usdAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-1_000_000), BigDecimal.valueOf(1_000_000))
                .ofScale(2);
    }
}
