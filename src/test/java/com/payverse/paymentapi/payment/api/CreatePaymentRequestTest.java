package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.payment.domain.CaptureMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CreatePaymentRequestTest {

    private static String hash(String amount, String currency, CaptureMethod captureMethod, String reference) {
        return new CreatePaymentRequest(new BigDecimal(amount), currency, captureMethod, reference).requestHash();
    }

    @Test
    void equivalentRequestsHaveTheSameHash() {
        assertEquals(hash("49.90", "USD", CaptureMethod.MANUAL, "order-1"),
                hash("49.9", "usd", CaptureMethod.MANUAL, "order-1"));
    }

    @Test
    void anyMeaningfulDifferenceChangesTheHash() {
        String original = hash("49.90", "USD", CaptureMethod.MANUAL, "order-1");

        assertNotEquals(original, hash("49.91", "USD", CaptureMethod.MANUAL, "order-1"));
        assertNotEquals(original, hash("49.90", "EUR", CaptureMethod.MANUAL, "order-1"));
        assertNotEquals(original, hash("49.90", "USD", CaptureMethod.AUTOMATIC, "order-1"));
        assertNotEquals(original, hash("49.90", "USD", CaptureMethod.MANUAL, "order-2"));
        assertNotEquals(original, hash("49.90", "USD", CaptureMethod.MANUAL, null));
    }
}
