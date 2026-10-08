package com.payverse.paymentapi.payment.domain;

import java.util.UUID;

public class PaymentInvalidStateException extends RuntimeException {

    public PaymentInvalidStateException(UUID paymentId, PaymentStatus current, PaymentStatus requested) {
        super("Payment " + paymentId + " is " + current + " and cannot move to " + requested);
    }
}
