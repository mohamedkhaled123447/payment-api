package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Api.PayerAuthenticationApi;
import Invokers.ApiException;
import Model.PayerAuthSetupRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CybersourceClientTest {

    @Test
    void wrapsCybersourceApiErrors() throws Exception {
        PayerAuthenticationApi payerAuthenticationApi = mock(PayerAuthenticationApi.class);
        CybersourceClient client = new CybersourceClient(payerAuthenticationApi);
        PayerAuthSetupRequest request = new PayerAuthSetupRequest();
        when(payerAuthenticationApi.payerAuthSetup(request)).thenThrow(new ApiException(502, "Cybersource error"));

        assertThrows(CybersourceClientException.class, () -> client.authenticationSetup(request));
    }
}
