package com.payverse.paymentapi.idempotency;

import java.util.UUID;

/**
 * The response of an idempotent operation: either just produced, or replayed from an earlier
 * request with the same key.
 */
public record IdempotentResult<T>(int status, T body, UUID paymentId, boolean replayed) {

    public static <T> IdempotentResult<T> of(int status, T body, UUID paymentId) {
        return new IdempotentResult<>(status, body, paymentId, false);
    }
}
