package com.payverse.paymentapi.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a payment. See docs/design/payments.md, section 3.
 */
public enum PaymentStatus {
    CREATED,
    THREE_DS_PENDING,
    // Saved before the authorization call; a timeout or crash leaves the payment here (D3, D4).
    AUTHORIZING,
    PENDING_REVIEW,
    AUTHORIZED,
    CAPTURED,
    VOIDED,
    DECLINED,
    FAILED;

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(THREE_DS_PENDING, AUTHORIZING);
            case THREE_DS_PENDING -> EnumSet.of(AUTHORIZING);
            // CAPTURED directly from AUTHORIZING is a sale (automatic capture).
            case AUTHORIZING -> EnumSet.of(AUTHORIZED, PENDING_REVIEW, DECLINED, FAILED, CAPTURED);
            case PENDING_REVIEW -> EnumSet.of(AUTHORIZED, DECLINED);
            case AUTHORIZED -> EnumSet.of(CAPTURED, VOIDED);
            // CAPTURED stays final until refunds are added.
            case CAPTURED, VOIDED, DECLINED, FAILED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }
}
