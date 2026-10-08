package com.payverse.paymentapi.idempotency;

public class IdempotencyKeyInProgressException extends RuntimeException {

    public IdempotencyKeyInProgressException() {
        super("A request with this Idempotency-Key is still being processed; retry later");
    }
}
