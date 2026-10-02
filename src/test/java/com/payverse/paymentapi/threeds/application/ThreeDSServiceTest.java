package com.payverse.paymentapi.threeds.application;

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
import com.payverse.paymentapi.threeds.persistence.ThreeDSProviderType;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSession;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionRepository;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThreeDSServiceTest {

    @Test
    void setupPersistsTheProviderSessionAndReturnsAGeneratedPaymentId() {
        ThreeDSProvider provider = mock(ThreeDSProvider.class);
        ThreeDSSessionRepository repository = mock(ThreeDSSessionRepository.class);
        ThreeDSService service = new ThreeDSService(provider, repository);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(
                new ThreeDSCard("4111111111111111", "12", "2028"));
        ThreeDSSetupResponse providerResponse = new ThreeDSSetupResponse(
                "reference-id",
                "access-token",
                "https://device-data.example");
        when(provider.setup(request)).thenReturn(providerResponse);

        ThreeDSSetupResponse actual = service.setup(request);

        assertEquals(providerResponse.referenceId(), actual.referenceId());
        assertEquals(providerResponse.accessToken(), actual.accessToken());
        assertEquals(providerResponse.deviceDataCollectionUrl(), actual.deviceDataCollectionUrl());
        assertNotNull(actual.paymentId());
        verify(provider).setup(request);
        ArgumentCaptor<ThreeDSSession> sessionCaptor = ArgumentCaptor.forClass(ThreeDSSession.class);
        verify(repository).save(sessionCaptor.capture());

        ThreeDSSession session = sessionCaptor.getValue();
        assertEquals(actual.paymentId(), session.getPaymentId());
        assertEquals(ThreeDSProviderType.CYBERSOURCE, session.getProvider());
        assertEquals("reference-id", session.getProviderReferenceId());
        assertEquals(ThreeDSSessionStatus.SETUP_COMPLETED, session.getStatus());
    }

    @Test
    void setupDoesNotPersistASessionWhenTheProviderFails() {
        ThreeDSProvider provider = mock(ThreeDSProvider.class);
        ThreeDSSessionRepository repository = mock(ThreeDSSessionRepository.class);
        ThreeDSService service = new ThreeDSService(provider, repository);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(
                new ThreeDSCard("4111111111111111", "12", "2028"));
        when(provider.setup(request)).thenThrow(new ThreeDSProviderException("Cybersource setup failed"));

        assertThrows(ThreeDSProviderException.class, () -> service.setup(request));

        verify(provider).setup(request);
        verifyNoInteractions(repository);
    }

    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private final ThreeDSProvider provider = mock(ThreeDSProvider.class);
    private final ThreeDSSessionRepository repository = mock(ThreeDSSessionRepository.class);
    private final ThreeDSService service = new ThreeDSService(provider, repository);

    private ThreeDSEnrollmentRequest enrollmentRequest() {
        return new ThreeDSEnrollmentRequest(
                PAYMENT_ID,
                new ThreeDSCard("4111111111111111", "12", "2028"),
                new BigDecimal("49.90"),
                "usd",
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
        assertEquals("USD", captor.getValue().currency());
        assertEquals(PAYMENT_ID, captor.getValue().paymentId());
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
}
