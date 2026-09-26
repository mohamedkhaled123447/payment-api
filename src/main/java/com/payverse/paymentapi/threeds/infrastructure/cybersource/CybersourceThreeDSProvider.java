package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Model.PayerAuthSetupRequest;
import Model.RiskV1AuthenticationSetupsPost201Response;
import Model.RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation;
import Model.Riskv1authenticationsetupsPaymentInformation;
import Model.Riskv1authenticationsetupsPaymentInformationCard;
import com.payverse.paymentapi.threeds.application.ThreeDSProvider;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import org.springframework.stereotype.Component;

@Component
public class CybersourceThreeDSProvider implements ThreeDSProvider {

    private final CybersourceClient cybersourceClient;

    public CybersourceThreeDSProvider(CybersourceClient cybersourceClient) {
        this.cybersourceClient = cybersourceClient;
    }

    @Override
    public ThreeDSSetupResponse setup(ThreeDSSetupRequest request) {
        try {
            RiskV1AuthenticationSetupsPost201Response response = cybersourceClient.authenticationSetup(toCybersourceRequest(request));
            return toSetupResponse(response);
        } catch (CybersourceClientException exception) {
            throw new ThreeDSProviderException("3DS authentication setup failed", exception);
        }
    }

    private PayerAuthSetupRequest toCybersourceRequest(ThreeDSSetupRequest request) {
        Riskv1authenticationsetupsPaymentInformationCard card = new Riskv1authenticationsetupsPaymentInformationCard()
                .number(request.card().number())
                .expirationMonth(request.card().expirationMonth())
                .expirationYear(request.card().expirationYear());

        return new PayerAuthSetupRequest()
                .paymentInformation(new Riskv1authenticationsetupsPaymentInformation().card(card));
    }

    private ThreeDSSetupResponse toSetupResponse(RiskV1AuthenticationSetupsPost201Response response) {
        RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation authenticationInformation =
                response == null ? null : response.getConsumerAuthenticationInformation();

        if (authenticationInformation == null
                || authenticationInformation.getReferenceId() == null
                || authenticationInformation.getAccessToken() == null
                || authenticationInformation.getDeviceDataCollectionUrl() == null) {
            throw new ThreeDSProviderException("Cybersource authentication setup response is incomplete");
        }

        return new ThreeDSSetupResponse(
                authenticationInformation.getReferenceId(),
                authenticationInformation.getAccessToken(),
                authenticationInformation.getDeviceDataCollectionUrl());
    }
}
