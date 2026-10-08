package com.payverse.paymentapi.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private static Payment create(String amount, String currency) {
        return Payment.create(new BigDecimal(amount), currency, CaptureMethod.MANUAL, "order-1");
    }

    @Test
    void createStartsInCreatedWithAnIdAndNormalizedAmountAndCurrency() {
        Payment payment = create("49.9", "usd");

        assertNotNull(payment.getId());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        assertEquals("49.90", payment.getAmount().toPlainString());
        assertEquals("USD", payment.getCurrency());
        assertEquals(CaptureMethod.MANUAL, payment.getCaptureMethod());
        assertEquals("order-1", payment.getMerchantReference());
    }

    @Test
    void amountUsesTheCurrencysMinorUnits() {
        assertEquals("1000", create("1000", "JPY").getAmount().toPlainString());
        assertEquals("1.250", create("1.25", "BHD").getAmount().toPlainString());
        // Trailing zeros beyond the minor units are not extra precision.
        assertEquals("10.50", create("10.5000", "USD").getAmount().toPlainString());
    }

    @Test
    void createRejectsAmountsTheCurrencyCannotRepresent() {
        assertThrows(InvalidPaymentException.class, () -> create("49.905", "USD"));
        assertThrows(InvalidPaymentException.class, () -> create("10.5", "JPY"));
        assertThrows(InvalidPaymentException.class, () -> create("0", "USD"));
        assertThrows(InvalidPaymentException.class, () -> create("-1", "USD"));
        assertThrows(InvalidPaymentException.class, () -> create("1000000000000000", "USD"));
        assertThrows(InvalidPaymentException.class,
                () -> Payment.create(null, "USD", CaptureMethod.MANUAL, null));
    }

    @Test
    void createRejectsUnknownAndNonPayableCurrencies() {
        assertThrows(InvalidPaymentException.class, () -> create("10", "ABC"));
        assertThrows(InvalidPaymentException.class, () -> create("10", "XAU"));
        assertThrows(InvalidPaymentException.class, () -> create("10", null));
    }

    @Test
    void createRequiresACaptureMethod() {
        assertThrows(InvalidPaymentException.class,
                () -> Payment.create(BigDecimal.TEN, "USD", null, null));
    }

    @Test
    void startThreeDSMovesACreatedPaymentToThreeDSPending() {
        Payment payment = create("10", "USD");

        payment.ensureCanStartThreeDS();
        payment.startThreeDS();

        assertEquals(PaymentStatus.THREE_DS_PENDING, payment.getStatus());
    }

    @Test
    void threeDSCanOnlyStartOnce() {
        Payment payment = create("10", "USD");
        payment.startThreeDS();

        assertThrows(PaymentInvalidStateException.class, payment::ensureCanStartThreeDS);
        assertThrows(PaymentInvalidStateException.class, payment::startThreeDS);
        assertEquals(PaymentStatus.THREE_DS_PENDING, payment.getStatus());
    }
}
