package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.domain.CaptureMethod;
import com.payverse.paymentapi.payment.domain.Payment;
import com.payverse.paymentapi.payment.domain.PaymentInvalidStateException;
import com.payverse.paymentapi.payment.domain.PaymentStatus;
import com.payverse.paymentapi.payment.persistence.PaymentRepository;
import com.payverse.paymentapi.threeds.model.ThreeDSBillTo;
import com.payverse.paymentapi.threeds.model.ThreeDSBrowser;
import com.payverse.paymentapi.threeds.model.ThreeDSCard;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResult;
import com.payverse.paymentapi.threeds.persistence.ThreeDSProviderType;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSession;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionRepository;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThreeDSServiceTest {

    // --- setup ---

    private ThreeDSSetupRequest setupRequest(Payment payment) {
        return new ThreeDSSetupRequest(payment.getId(), new ThreeDSCard("4111111111111111", "12", "2028"));
    }

    @Test
    void setupStartsThreeDSOnTheExistingPaymentAndPersistsTheProviderSession() {
        Payment payment = newPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        ThreeDSSetupRequest request = setupRequest(payment);
        ThreeDSSetupResponse providerResponse = new ThreeDSSetupResponse(
                "reference-id",
                "access-token",
                "https://device-data.example");
        when(provider.setup(request)).thenReturn(providerResponse);

        ThreeDSSetupResponse actual = service.setup(request);

        assertEquals(providerResponse.referenceId(), actual.referenceId());
        assertEquals(providerResponse.accessToken(), actual.accessToken());
        assertEquals(providerResponse.deviceDataCollectionUrl(), actual.deviceDataCollectionUrl());
        assertEquals(payment.getId(), actual.paymentId());
        assertEquals(PaymentStatus.THREE_DS_PENDING, payment.getStatus());
        verify(paymentRepository).save(payment);
        ArgumentCaptor<ThreeDSSession> sessionCaptor = ArgumentCaptor.forClass(ThreeDSSession.class);
        verify(repository).save(sessionCaptor.capture());

        ThreeDSSession session = sessionCaptor.getValue();
        assertEquals(payment.getId(), session.getPaymentId());
        assertEquals(ThreeDSProviderType.CYBERSOURCE, session.getProvider());
        assertEquals("reference-id", session.getProviderReferenceId());
        assertEquals(ThreeDSSessionStatus.SETUP_COMPLETED, session.getStatus());
    }

    @Test
    void setupDoesNotChangeThePaymentOrPersistASessionWhenTheProviderFails() {
        Payment payment = newPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        ThreeDSSetupRequest request = setupRequest(payment);
        when(provider.setup(request)).thenThrow(new ThreeDSProviderException("Cybersource setup failed"));

        assertThrows(ThreeDSProviderException.class, () -> service.setup(request));

        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(repository, never()).save(any());
    }

    @Test
    void setupRejectsAnUnknownPaymentWithoutCallingTheProvider() {
        Payment payment = newPayment();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.setup(setupRequest(payment)));

        verifyNoInteractions(provider);
    }

    @Test
    void setupRejectsAPaymentThatAlreadyStartedThreeDSWithoutCallingTheProvider() {
        Payment payment = newPayment();
        payment.startThreeDS();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThrows(PaymentInvalidStateException.class, () -> service.setup(setupRequest(payment)));

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    // --- enrollment ---

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(2);

    private final ThreeDSProvider provider = mock(ThreeDSProvider.class);
    private final ThreeDSSessionRepository repository = mock(ThreeDSSessionRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    // A real template over a mock transaction manager runs the callback without a database.
    private final ThreeDSService service = new ThreeDSService(
            provider,
            repository,
            paymentRepository,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            CLAIM_TIMEOUT);

    private static Payment newPayment() {
        return Payment.create(new BigDecimal("49.9"), "usd", CaptureMethod.MANUAL, null);
    }

    @BeforeEach
    void paymentOfTheEnrolledSessionExists() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(newPayment()));
    }

    private ThreeDSEnrollmentRequest enrollmentRequest() {
        return new ThreeDSEnrollmentRequest(
                PAYMENT_ID,
                new ThreeDSCard("4111111111111111", "12", "2028"),
                new ThreeDSBillTo("Ann", "Lee", "ann@example.com", "1 Main St", "Austin", "TX", "73301", "US"),
                new ThreeDSBrowser("text/html", "agent", "en-US", false, "24", "1080", "1920", "300"),
                "https://shop.example/3ds/return");
    }

    private ThreeDSSession session(ThreeDSSessionStatus status) {
        ThreeDSSession session = new ThreeDSSession();
        session.setPaymentId(PAYMENT_ID);
        session.setProvider(ThreeDSProviderType.CYBERSOURCE);
        session.setProviderReferenceId("reference-id");
        session.setStatus(status);
        return session;
    }

    private ThreeDSEnrollmentResult result(ThreeDSEnrollmentOutcome outcome) {
        return new ThreeDSEnrollmentResult(
                outcome, "auth-tx-id", "https://step-up.example", "step-up-jwt",
                "cavv-value", "05", "vbv", "xid-value", "2.2.0", "ds-tx-id", "Y");
    }

    @Test
    void enrollSendsTheStoredReferenceIdAndClientIpToTheProvider() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session(ThreeDSSessionStatus.SETUP_COMPLETED)));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS));

        service.enroll(enrollmentRequest(), "203.0.113.7");

        ArgumentCaptor<ThreeDSEnrollmentCommand> captor = ArgumentCaptor.forClass(ThreeDSEnrollmentCommand.class);
        verify(provider).enroll(captor.capture());
        assertEquals("reference-id", captor.getValue().referenceId());
        assertEquals("203.0.113.7", captor.getValue().ipAddress());
        assertEquals(PAYMENT_ID, captor.getValue().paymentId());
    }

    @Test
    void enrollTakesAmountAndCurrencyFromThePayment() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session(ThreeDSSessionStatus.SETUP_COMPLETED)));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS));

        service.enroll(enrollmentRequest(), "203.0.113.7");

        ArgumentCaptor<ThreeDSEnrollmentCommand> captor = ArgumentCaptor.forClass(ThreeDSEnrollmentCommand.class);
        verify(provider).enroll(captor.capture());
        assertEquals(new BigDecimal("49.90"), captor.getValue().amount());
        assertEquals("USD", captor.getValue().currency());
    }

    @Test
    void enrollRejectsASessionWhosePaymentDoesNotExist() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session(ThreeDSSessionStatus.SETUP_COMPLETED)));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.enroll(enrollmentRequest(), "203.0.113.7"));

        verifyNoInteractions(provider);
    }

    @Test
    void frictionlessEnrollmentAuthenticatesTheSessionAndHidesAuthenticationValues() {
        ThreeDSSession session = session(ThreeDSSessionStatus.SETUP_COMPLETED);
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS));

        ThreeDSEnrollmentResponse response = service.enroll(enrollmentRequest(), "203.0.113.7");

        assertEquals(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS, response.outcome());
        assertNull(response.stepUpUrl());
        assertNull(response.accessToken());
        assertEquals(ThreeDSSessionStatus.AUTHENTICATED, session.getStatus());
        assertEquals("cavv-value", session.getAuthenticationValue());
        assertEquals("05", session.getEci());
        assertEquals(new BigDecimal("49.90"), session.getAmount());
        assertEquals("USD", session.getCurrency());
        verify(repository).save(session);
    }

    @Test
    void challengeEnrollmentReturnsStepUpDataAndMarksTheSessionChallengeRequired() {
        ThreeDSSession session = session(ThreeDSSessionStatus.SETUP_COMPLETED);
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED));

        ThreeDSEnrollmentResponse response = service.enroll(enrollmentRequest(), "203.0.113.7");

        assertEquals(ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED, response.outcome());
        assertEquals("https://step-up.example", response.stepUpUrl());
        assertEquals("step-up-jwt", response.accessToken());
        assertEquals("auth-tx-id", response.authenticationTransactionId());
        assertEquals(ThreeDSSessionStatus.CHALLENGE_REQUIRED, session.getStatus());
    }

    @Test
    void failedAndUnavailableOutcomesAreRecordedWithoutStepUpData() {
        ThreeDSSession failed = session(ThreeDSSessionStatus.SETUP_COMPLETED);
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(failed));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.FAILED));
        assertNull(service.enroll(enrollmentRequest(), "203.0.113.7").stepUpUrl());
        assertEquals(ThreeDSSessionStatus.AUTHENTICATION_FAILED, failed.getStatus());

        ThreeDSSession unavailable = session(ThreeDSSessionStatus.SETUP_COMPLETED);
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(unavailable));
        when(provider.enroll(any())).thenReturn(result(ThreeDSEnrollmentOutcome.UNAVAILABLE));
        service.enroll(enrollmentRequest(), "203.0.113.7");
        assertEquals(ThreeDSSessionStatus.AUTHENTICATION_UNAVAILABLE, unavailable.getStatus());
    }

    @Test
    void enrollRejectsAnUnknownPayment() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ThreeDSSessionNotFoundException.class, () -> service.enroll(enrollmentRequest(), "203.0.113.7"));

        verifyNoInteractions(provider);
    }

    @Test
    void enrollRejectsASessionThatIsNotInSetupCompleted() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session(ThreeDSSessionStatus.AUTHENTICATED)));

        assertThrows(ThreeDSInvalidStateException.class, () -> service.enroll(enrollmentRequest(), "203.0.113.7"));

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void enrollLeavesTheSessionUntouchedWhenTheProviderFails() {
        ThreeDSSession session = session(ThreeDSSessionStatus.SETUP_COMPLETED);
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.enroll(any())).thenThrow(new ThreeDSProviderException("boom"));

        assertThrows(ThreeDSProviderException.class, () -> service.enroll(enrollmentRequest(), "203.0.113.7"));

        assertEquals(ThreeDSSessionStatus.SETUP_COMPLETED, session.getStatus());
        verify(repository, never()).save(any());
    }

    // --- validation ---

    // Status of the session at each save(), in order. Lets the tests assert the claim happens
    // before the provider call and the result after it.
    private final List<ThreeDSSessionStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void saveReturnsTheEntityAndRecordsItsStatus() {
        when(repository.save(any())).thenAnswer(invocation -> {
            ThreeDSSession saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });
    }

    private ThreeDSValidationRequest validationRequest() {
        return new ThreeDSValidationRequest(PAYMENT_ID, new ThreeDSCard("4111111111111111", "12", "2028"));
    }

    private ThreeDSSession challengedSession() {
        ThreeDSSession session = session(ThreeDSSessionStatus.CHALLENGE_REQUIRED);
        session.setAuthenticationTransactionId("auth-tx-id");
        session.setAmount(new BigDecimal("49.90"));
        session.setCurrency("USD");
        return session;
    }

    private ThreeDSValidationResult validationResult(ThreeDSValidationOutcome outcome) {
        return new ThreeDSValidationResult(outcome, "validated-cavv", "05", "vbv", "validated-xid", "2.2.0", "ds-tx-id");
    }

    @Test
    void validateClaimsTheSessionBeforeCallingTheProviderThenAuthenticatesIt() {
        ThreeDSSession session = challengedSession();
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.validate(any())).thenReturn(validationResult(ThreeDSValidationOutcome.AUTHENTICATED));

        ThreeDSValidationResponse response = service.validate(validationRequest());

        assertEquals(new ThreeDSValidationResponse(PAYMENT_ID, ThreeDSValidationOutcome.AUTHENTICATED), response);
        assertEquals(List.of(ThreeDSSessionStatus.VALIDATING, ThreeDSSessionStatus.AUTHENTICATED), savedStatuses);
        assertEquals("validated-cavv", session.getAuthenticationValue());
        assertEquals("05", session.getEci());
        assertEquals("validated-xid", session.getXid());
    }

    @Test
    void validateSendsTheSessionTransactionIdAmountAndCurrencyWithTheRequestCard() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(challengedSession()));
        when(provider.validate(any())).thenReturn(validationResult(ThreeDSValidationOutcome.AUTHENTICATED));

        service.validate(validationRequest());

        ArgumentCaptor<ThreeDSValidationCommand> captor = ArgumentCaptor.forClass(ThreeDSValidationCommand.class);
        verify(provider).validate(captor.capture());
        ThreeDSValidationCommand command = captor.getValue();
        assertEquals(PAYMENT_ID, command.paymentId());
        assertEquals("auth-tx-id", command.authenticationTransactionId());
        assertEquals(new BigDecimal("49.90"), command.amount());
        assertEquals("USD", command.currency());
        assertEquals("4111111111111111", command.card().number());
    }

    @Test
    void validateRecordsAFailedAuthenticationWithoutStoringAuthenticationValues() {
        ThreeDSSession session = challengedSession();
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.validate(any())).thenReturn(validationResult(ThreeDSValidationOutcome.FAILED));

        ThreeDSValidationResponse response = service.validate(validationRequest());

        assertEquals(ThreeDSValidationOutcome.FAILED, response.outcome());
        assertEquals(List.of(ThreeDSSessionStatus.VALIDATING, ThreeDSSessionStatus.AUTHENTICATION_FAILED), savedStatuses);
        assertNull(session.getAuthenticationValue());
    }

    @Test
    void validateKeepsEnrollmentValuesThatTheValidationResponseOmits() {
        ThreeDSSession session = challengedSession();
        session.setSpecificationVersion("2.1.0");
        session.setDirectoryServerTransactionId("enrollment-ds-tx");
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.validate(any())).thenReturn(new ThreeDSValidationResult(
                ThreeDSValidationOutcome.AUTHENTICATED, "validated-cavv", "05", null, "xid", null, null));

        service.validate(validationRequest());

        assertEquals("2.1.0", session.getSpecificationVersion());
        assertEquals("enrollment-ds-tx", session.getDirectoryServerTransactionId());
    }

    @Test
    void validateReturnsTheStoredResultOfAFinishedValidationWithoutCallingTheProvider() {
        when(repository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(session(ThreeDSSessionStatus.AUTHENTICATED)))
                .thenReturn(Optional.of(session(ThreeDSSessionStatus.AUTHENTICATION_FAILED)));

        assertEquals(ThreeDSValidationOutcome.AUTHENTICATED, service.validate(validationRequest()).outcome());
        assertEquals(ThreeDSValidationOutcome.FAILED, service.validate(validationRequest()).outcome());

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void validateRejectsAnUnknownPayment() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThrows(ThreeDSSessionNotFoundException.class, () -> service.validate(validationRequest()));

        verifyNoInteractions(provider);
    }

    @Test
    void validateRejectsASessionThatHasNotBeenChallenged() {
        when(repository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(session(ThreeDSSessionStatus.SETUP_COMPLETED)))
                .thenReturn(Optional.of(session(ThreeDSSessionStatus.AUTHENTICATION_UNAVAILABLE)));

        assertThrows(ThreeDSInvalidStateException.class, () -> service.validate(validationRequest()));
        assertThrows(ThreeDSInvalidStateException.class, () -> service.validate(validationRequest()));

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void validateRejectsARequestWhileAnotherValidationIsInFlight() {
        ThreeDSSession session = session(ThreeDSSessionStatus.VALIDATING);
        session.setUpdatedAt(Instant.now());
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));

        assertThrows(ThreeDSInvalidStateException.class, () -> service.validate(validationRequest()));

        verifyNoInteractions(provider);
        verify(repository, never()).save(any());
    }

    @Test
    void validateReclaimsAClaimThatHasExpired() {
        ThreeDSSession session = challengedSession();
        session.setStatus(ThreeDSSessionStatus.VALIDATING);
        session.setUpdatedAt(Instant.now().minus(CLAIM_TIMEOUT).minusSeconds(60));
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.validate(any())).thenReturn(validationResult(ThreeDSValidationOutcome.AUTHENTICATED));

        ThreeDSValidationResponse response = service.validate(validationRequest());

        assertEquals(ThreeDSValidationOutcome.AUTHENTICATED, response.outcome());
        // The stale claim is released with a real change first, then claimed again, then finished.
        assertEquals(List.of(
                ThreeDSSessionStatus.CHALLENGE_REQUIRED,
                ThreeDSSessionStatus.VALIDATING,
                ThreeDSSessionStatus.AUTHENTICATED), savedStatuses);
    }

    @Test
    void validateDoesNotCallTheProviderWhenAnotherRequestWinsTheClaim() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(challengedSession()));
        doThrow(new OptimisticLockingFailureException("claimed by another request")).when(repository).save(any());

        assertThrows(OptimisticLockingFailureException.class, () -> service.validate(validationRequest()));

        verifyNoInteractions(provider);
    }

    @Test
    void validateReleasesTheClaimWhenTheProviderFails() {
        ThreeDSSession session = challengedSession();
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(session));
        when(provider.validate(any())).thenThrow(new ThreeDSProviderException("boom"));

        assertThrows(ThreeDSProviderException.class, () -> service.validate(validationRequest()));

        assertEquals(List.of(ThreeDSSessionStatus.VALIDATING, ThreeDSSessionStatus.CHALLENGE_REQUIRED), savedStatuses);
        assertEquals(ThreeDSSessionStatus.CHALLENGE_REQUIRED, session.getStatus());
    }

    @Test
    void validateReportsTheProviderFailureEvenWhenReleasingTheClaimFails() {
        when(repository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(challengedSession()));
        ThreeDSProviderException providerFailure = new ThreeDSProviderException("boom");
        when(provider.validate(any())).thenThrow(providerFailure);
        OptimisticLockingFailureException releaseFailure = new OptimisticLockingFailureException("release failed");
        doAnswer(invocation -> invocation.getArgument(0))
                .doThrow(releaseFailure)
                .when(repository).save(any());

        ThreeDSProviderException thrown =
                assertThrows(ThreeDSProviderException.class, () -> service.validate(validationRequest()));

        assertSame(providerFailure, thrown);
        assertSame(releaseFailure, thrown.getSuppressed()[0]);
    }
}
