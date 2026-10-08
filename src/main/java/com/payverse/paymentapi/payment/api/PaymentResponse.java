package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.payment.domain.CaptureMethod;
import com.payverse.paymentapi.payment.domain.Payment;
import com.payverse.paymentapi.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code amount} is a decimal string so JSON clients can't lose precision to floating point (D8). */
public record PaymentResponse(
        UUID id,
        PaymentStatus status,
        String amount,
        String currency,
        CaptureMethod captureMethod,
        String merchantReference,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount().toPlainString(),
                payment.getCurrency(),
                payment.getCaptureMethod(),
                payment.getMerchantReference(),
                payment.getCreatedAt());
    }
}
