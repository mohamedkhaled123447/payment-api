package com.payverse.paymentapi.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void normalizesAmountToCurrencyFractionDigits() {
        Money usd = Money.of(new BigDecimal("10"), "USD");
        Money jpy = Money.of(new BigDecimal("42.0"), "JPY");
        Money bhd = Money.of(new BigDecimal("1.234"), "BHD");

        assertEquals(new BigDecimal("10.00"), usd.amount());
        assertEquals(new BigDecimal("42"), jpy.amount());
        assertEquals(new BigDecimal("1.234"), bhd.amount());
    }

    @Test
    void acceptsAndRemovesHarmlessTrailingZeros() {
        Money money = Money.of(new BigDecimal("10.0000"), "USD");

        assertEquals(new BigDecimal("10.00"), money.amount());
    }

    @Test
    void normalizesCurrencyCodeForFactoryConstruction() {
        Money money = Money.of(new BigDecimal("10"), " usd ");

        assertEquals(Currency.getInstance("USD"), money.currency());
    }

    @Test
    void equivalentValuesHaveRecordEqualityAndHashCode() {
        Money whole = Money.of(new BigDecimal("10"), "USD");
        Money scaled = Money.of(new BigDecimal("10.00"), "USD");

        assertEquals(whole, scaled);
        assertEquals(whole.hashCode(), scaled.hashCode());
        assertEquals("Money[amount=10.00, currency=USD]", whole.toString());
    }

    @Test
    void differentAmountsOrCurrenciesAreNotEqual() {
        Money tenUsd = Money.of(new BigDecimal("10"), "USD");

        assertNotEquals(tenUsd, Money.of(new BigDecimal("11"), "USD"));
        assertNotEquals(tenUsd, Money.of(new BigDecimal("10"), "EUR"));
    }

    @Test
    void rejectsNullValues() {
        Currency usd = Currency.getInstance("USD");

        assertThrows(NullPointerException.class, () -> new Money(null, usd));
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class, () -> Money.of(BigDecimal.ONE, null));
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ZERO, "USD"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("-0.01"), "USD"));
    }

    @Test
    void rejectsExcessFractionDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("10.001"), "USD"));
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.of(new BigDecimal("1.1"), "JPY"));
    }

    @Test
    void rejectsInvalidOrUnsupportedCurrencyCodes() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ONE, "US"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ONE, "XXX"));
    }
}
