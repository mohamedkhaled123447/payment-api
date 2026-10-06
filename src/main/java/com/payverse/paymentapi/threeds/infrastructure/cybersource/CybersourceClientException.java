package com.payverse.paymentapi.threeds.infrastructure.cybersource;

public class CybersourceClientException extends RuntimeException {

    public CybersourceClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
