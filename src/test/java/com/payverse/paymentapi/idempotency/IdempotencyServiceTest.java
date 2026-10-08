package com.payverse.paymentapi.idempotency;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    record Body(String value, int number) {
    }

    private static final String KEY = "key-1";
    private static final String HASH = "hash-1";
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);
    private final IdempotencyService service = new IdempotencyService(
            repository, new TransactionTemplate(mock(PlatformTransactionManager.class)), JSON);

    private final AtomicInteger operationRuns = new AtomicInteger();
    private final Supplier<IdempotentResult<Body>> operation = () -> {
        operationRuns.incrementAndGet();
        return IdempotentResult.of(201, new Body("created", 7), PAYMENT_ID);
    };

    private IdempotentResult<Body> execute(String key, String hash) {
        return service.execute(key, hash, Body.class, operation);
    }

    private static IdempotencyKey completedRecord(String hash) {
        IdempotencyKey record = IdempotencyKey.start(KEY, hash);
        record.complete(201, JSON.writeValueAsString(new Body("stored", 3)), PAYMENT_ID);
        return record;
    }

    @Test
    void aNewKeyRunsTheOperationOnceAndStoresItsResponse() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IdempotentResult<Body> result = execute(KEY, HASH);

        assertEquals(new IdempotentResult<>(201, new Body("created", 7), PAYMENT_ID, false), result);
        assertEquals(1, operationRuns.get());
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(captor.capture());
        IdempotencyKey stored = captor.getValue();
        assertEquals(KEY, stored.getKey());
        assertEquals(HASH, stored.getRequestHash());
        assertEquals(IdempotencyKeyStatus.COMPLETED, stored.getStatus());
        assertEquals(201, stored.getResponseStatus());
        assertEquals(new Body("created", 7), JSON.readValue(stored.getResponseBody(), Body.class));
        assertEquals(PAYMENT_ID, stored.getPaymentId());
    }

    @Test
    void theKeyIsInsertedAsANewRowSoADuplicateFailsOnThePrimaryKey() {
        IdempotencyKey record = IdempotencyKey.start(KEY, HASH);

        assertTrue(record.isNew());
        assertEquals(IdempotencyKeyStatus.IN_PROGRESS, record.getStatus());
        record.markStored();
        assertFalse(record.isNew());
    }

    @Test
    void aRetryWithTheSameRequestReplaysTheStoredResponse() {
        when(repository.findById(KEY)).thenReturn(Optional.of(completedRecord(HASH)));

        IdempotentResult<Body> result = execute(KEY, HASH);

        assertEquals(new IdempotentResult<>(201, new Body("stored", 3), PAYMENT_ID, true), result);
        assertEquals(0, operationRuns.get());
    }

    @Test
    void aKeyReusedWithADifferentRequestIsRejected() {
        when(repository.findById(KEY)).thenReturn(Optional.of(completedRecord(HASH)));

        assertThrows(IdempotencyKeyReusedException.class, () -> execute(KEY, "other-hash"));

        assertEquals(0, operationRuns.get());
    }

    @Test
    void aKeyWhoseFirstRequestIsStillRunningIsRejected() {
        when(repository.findById(KEY)).thenReturn(Optional.of(IdempotencyKey.start(KEY, HASH)));

        assertThrows(IdempotencyKeyInProgressException.class, () -> execute(KEY, HASH));

        assertEquals(0, operationRuns.get());
    }

    @Test
    void losingTheInsertRaceReplaysTheWinnersResponse() {
        // Both requests saw no key; the other request's insert committed first.
        when(repository.findById(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completedRecord(HASH)));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        IdempotentResult<Body> result = execute(KEY, HASH);

        assertTrue(result.replayed());
        assertEquals(new Body("stored", 3), result.body());
        assertEquals(0, operationRuns.get());
    }

    @Test
    void losingTheInsertRaceWithADifferentRequestIsRejected() {
        when(repository.findById(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completedRecord(HASH)));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IdempotencyKeyReusedException.class, () -> execute(KEY, "other-hash"));
    }

    @Test
    void losingTheInsertRaceToARequestThatThenRolledBackAsksTheClientToRetry() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IdempotencyKeyInProgressException.class, () -> execute(KEY, HASH));
    }

    @Test
    void aFailingOperationPropagatesWithoutStoringAResponse() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.execute(KEY, HASH, Body.class, () -> {
                    throw failure;
                }));

        assertEquals(failure, thrown);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void blankAndOverlongKeysAreRejectedBeforeTouchingTheDatabase() {
        assertThrows(InvalidIdempotencyKeyException.class, () -> execute(null, HASH));
        assertThrows(InvalidIdempotencyKeyException.class, () -> execute("  ", HASH));
        assertThrows(InvalidIdempotencyKeyException.class,
                () -> execute("k".repeat(IdempotencyService.MAX_KEY_LENGTH + 1), HASH));

        verifyNoInteractions(repository);
        assertEquals(0, operationRuns.get());
    }
}
