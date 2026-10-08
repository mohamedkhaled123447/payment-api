package com.payverse.paymentapi.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

/**
 * Runs an operation at most once per Idempotency-Key and replays its response for retries (D6).
 */
@Service
public class IdempotencyService {

    static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyKeyRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    public IdempotencyService(
            IdempotencyKeyRepository repository,
            TransactionTemplate transactionTemplate,
            JsonMapper jsonMapper) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Runs {@code operation} in one transaction with the key's record, unless the key was used before.
     *
     * @throws InvalidIdempotencyKeyException     the key is blank or too long
     * @throws IdempotencyKeyReusedException      the key was used with a different request
     * @throws IdempotencyKeyInProgressException  the first request with this key hasn't finished
     */
    public <T> IdempotentResult<T> execute(
            String key, String requestHash, Class<T> responseType, Supplier<IdempotentResult<T>> operation) {
        validate(key);

        // Fast path for an ordinary retry that arrives after the first request finished.
        var existing = repository.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, responseType);
        }

        try {
            return transactionTemplate.execute(status -> {
                IdempotencyKey record = claim(key, requestHash);
                IdempotentResult<T> result = operation.get();
                record.complete(result.status(), jsonMapper.writeValueAsString(result.body()), result.paymentId());
                repository.save(record);
                return result;
            });
        } catch (KeyAlreadyClaimed exception) {
            // A concurrent request inserted the key first. PostgreSQL made our insert wait until that
            // request committed, so its stored response is normally available now.
            return repository.findById(key)
                    .map(record -> replay(record, requestHash, responseType))
                    .orElseThrow(IdempotencyKeyInProgressException::new);
        }
    }

    // Insert the key before running the operation. The primary key lets exactly one concurrent
    // request succeed; check-then-insert would leave a window for both.
    private IdempotencyKey claim(String key, String requestHash) {
        try {
            return repository.saveAndFlush(IdempotencyKey.start(key, requestHash));
        } catch (DataIntegrityViolationException exception) {
            // Leave the transaction: after a failed statement PostgreSQL rejects every further one.
            throw new KeyAlreadyClaimed();
        }
    }

    private <T> IdempotentResult<T> replay(IdempotencyKey record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (record.getStatus() != IdempotencyKeyStatus.COMPLETED) {
            throw new IdempotencyKeyInProgressException();
        }
        T body = jsonMapper.readValue(record.getResponseBody(), responseType);
        return new IdempotentResult<>(record.getResponseStatus(), body, record.getPaymentId(), true);
    }

    private static void validate(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
    }

    private static final class KeyAlreadyClaimed extends RuntimeException {
        KeyAlreadyClaimed() {
            super(null, null, false, false);
        }
    }
}
