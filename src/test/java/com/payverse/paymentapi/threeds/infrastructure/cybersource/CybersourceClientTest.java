package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Api.PayerAuthenticationApi;
import Invokers.ApiException;
import Model.CheckPayerAuthEnrollmentRequest;
import Model.PayerAuthSetupRequest;
import Model.ValidateRequest;
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

    @Test
    void wrapsCybersourceApiErrorsFromTheEnrollmentCheck() throws Exception {
        PayerAuthenticationApi payerAuthenticationApi = mock(PayerAuthenticationApi.class);
        CybersourceClient client = new CybersourceClient(payerAuthenticationApi);
        CheckPayerAuthEnrollmentRequest request = new CheckPayerAuthEnrollmentRequest();
        when(payerAuthenticationApi.checkPayerAuthEnrollment(request)).thenThrow(new ApiException(502, "Cybersource error"));

        assertThrows(CybersourceClientException.class, () -> client.checkEnrollment(request));
    }

    @Test
    void wrapsCybersourceApiErrorsFromTheAuthenticationValidation() throws Exception {
        PayerAuthenticationApi payerAuthenticationApi = mock(PayerAuthenticationApi.class);
        CybersourceClient client = new CybersourceClient(payerAuthenticationApi);
        ValidateRequest request = new ValidateRequest();
        when(payerAuthenticationApi.validateAuthenticationResults(request)).thenThrow(new ApiException(502, "Cybersource error"));

        assertThrows(CybersourceClientException.class, () -> client.validateAuthentication(request));
    }
}
