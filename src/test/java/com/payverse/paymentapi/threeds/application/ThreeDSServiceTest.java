package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.model.ThreeDSCard;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.persistence.ThreeDSProviderType;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSession;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionRepository;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
}
