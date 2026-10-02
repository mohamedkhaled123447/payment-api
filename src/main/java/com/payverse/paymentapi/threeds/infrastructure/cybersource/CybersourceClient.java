package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Api.PayerAuthenticationApi;
import Invokers.ApiException;
import Model.CheckPayerAuthEnrollmentRequest;
import Model.PayerAuthSetupRequest;
import Model.RiskV1AuthenticationsPost201Response;
import Model.RiskV1AuthenticationSetupsPost201Response;
import com.cybersource.authsdk.core.ConfigException;
import org.springframework.stereotype.Component;

@Component
public class CybersourceClient {

    private final PayerAuthenticationApi payerAuthenticationApi;

    public CybersourceClient(PayerAuthenticationApi payerAuthenticationApi) {
        this.payerAuthenticationApi = payerAuthenticationApi;
    }

    public RiskV1AuthenticationSetupsPost201Response authenticationSetup(PayerAuthSetupRequest request) {
        try {
            return payerAuthenticationApi.payerAuthSetup(request);
        } catch (ApiException | ConfigException exception) {
            throw new CybersourceClientException("Cybersource authentication setup request failed", exception);
        }
    }

    public RiskV1AuthenticationsPost201Response checkEnrollment(CheckPayerAuthEnrollmentRequest request) {
        try {
            return payerAuthenticationApi.checkPayerAuthEnrollment(request);
        } catch (ApiException | ConfigException exception) {
            throw new CybersourceClientException("Cybersource enrollment check request failed", exception);
        }
    }
}
