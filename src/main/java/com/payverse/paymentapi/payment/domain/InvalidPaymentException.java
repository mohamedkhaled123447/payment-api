package com.payverse.paymentapi.payment.domain;

public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
