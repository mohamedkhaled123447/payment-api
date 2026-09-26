package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Model.PayerAuthSetupRequest;
import Model.RiskV1AuthenticationSetupsPost201Response;
import Model.RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.model.ThreeDSCard;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CybersourceThreeDSProviderTest {

    @Test
    void mapsTheSetupRequestAndResponse() {
        CybersourceClient client = mock(CybersourceClient.class);
        CybersourceThreeDSProvider provider = new CybersourceThreeDSProvider(client);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(new ThreeDSCard("4111111111111111", "12", "2028"));
        RiskV1AuthenticationSetupsPost201Response cybersourceResponse = new RiskV1AuthenticationSetupsPost201Response()
                .consumerAuthenticationInformation(new RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation()
                        .referenceId("reference-id")
                        .accessToken("access-token")
                        .deviceDataCollectionUrl("https://device-data.example"));
        when(client.authenticationSetup(org.mockito.ArgumentMatchers.any(PayerAuthSetupRequest.class)))
                .thenReturn(cybersourceResponse);

        ThreeDSSetupResponse response = provider.setup(request);

        ArgumentCaptor<PayerAuthSetupRequest> requestCaptor = ArgumentCaptor.forClass(PayerAuthSetupRequest.class);
        verify(client).authenticationSetup(requestCaptor.capture());
        assertEquals("4111111111111111", requestCaptor.getValue().getPaymentInformation().getCard().getNumber());
        assertEquals("12", requestCaptor.getValue().getPaymentInformation().getCard().getExpirationMonth());
        assertEquals("2028", requestCaptor.getValue().getPaymentInformation().getCard().getExpirationYear());
        assertEquals(new ThreeDSSetupResponse("reference-id", "access-token", "https://device-data.example"), response);
    }

    @Test
    void mapsCybersourceErrorsToProviderErrors() {
        CybersourceClient client = mock(CybersourceClient.class);
        CybersourceThreeDSProvider provider = new CybersourceThreeDSProvider(client);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(new ThreeDSCard("4111111111111111", "12", "2028"));
        when(client.authenticationSetup(org.mockito.ArgumentMatchers.any(PayerAuthSetupRequest.class)))
                .thenThrow(new CybersourceClientException("Cybersource error", new RuntimeException()));

        assertThrows(ThreeDSProviderException.class, () -> provider.setup(request));
    }
}
