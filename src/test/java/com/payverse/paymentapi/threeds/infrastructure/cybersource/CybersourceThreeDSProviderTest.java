package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Model.CheckPayerAuthEnrollmentRequest;
import Model.PayerAuthSetupRequest;
import Model.RiskV1AuthenticationSetupsPost201Response;
import Model.RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation;
import Model.RiskV1AuthenticationsPost201Response;
import Model.RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.model.ThreeDSBillTo;
import com.payverse.paymentapi.threeds.model.ThreeDSBrowser;
import com.payverse.paymentapi.threeds.model.ThreeDSCard;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CybersourceThreeDSProviderTest {

    @Test
    void mapsTheSetupRequestAndResponse() {
        CybersourceClient client = mock(CybersourceClient.class);
        CybersourceThreeDSProvider provider = new CybersourceThreeDSProvider(client);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(
                new ThreeDSCard("4111111111111111", "12", "2028"));
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
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(
                new ThreeDSCard("4111111111111111", "12", "2028"));
        when(client.authenticationSetup(org.mockito.ArgumentMatchers.any(PayerAuthSetupRequest.class)))
                .thenThrow(new CybersourceClientException("Cybersource error", new RuntimeException()));

        assertThrows(ThreeDSProviderException.class, () -> provider.setup(request));
    }

    private final CybersourceClient enrollmentClient = mock(CybersourceClient.class);
    private final CybersourceThreeDSProvider enrollmentProvider = new CybersourceThreeDSProvider(enrollmentClient);

    private ThreeDSEnrollmentCommand command(String cardNumber) {
        return new ThreeDSEnrollmentCommand(
                UUID.fromString("7f0c1f5e-0000-4000-8000-000000000001"),
                "reference-id",
                new ThreeDSCard(cardNumber, "12", "2028"),
                new BigDecimal("49.90"),
                "USD",
                new ThreeDSBillTo("Ann", "Lee", "ann@example.com", "1 Main St", "Austin", "TX", "73301", "US"),
                new ThreeDSBrowser("text/html", "agent", "en-US", false, "24", "1080", "1920", "300"),
                "203.0.113.7",
                "https://shop.example/3ds/return");
    }

    private RiskV1AuthenticationsPost201Response enrollmentResponse(
            String status, RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation info) {
        return new RiskV1AuthenticationsPost201Response().status(status).consumerAuthenticationInformation(info);
    }

    @Test
    void mapsTheEnrollmentRequest() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_SUCCESSFUL",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation().cavv("cavv").eci("05")));

        enrollmentProvider.enroll(command("4111111111111111"));

        ArgumentCaptor<CheckPayerAuthEnrollmentRequest> captor =
                ArgumentCaptor.forClass(CheckPayerAuthEnrollmentRequest.class);
        verify(enrollmentClient).checkEnrollment(captor.capture());
        CheckPayerAuthEnrollmentRequest request = captor.getValue();
        assertEquals("7f0c1f5e-0000-4000-8000-000000000001", request.getClientReferenceInformation().getCode());
        assertEquals("reference-id", request.getConsumerAuthenticationInformation().getReferenceId());
        assertEquals("https://shop.example/3ds/return", request.getConsumerAuthenticationInformation().getReturnUrl());
        assertEquals("001", request.getPaymentInformation().getCard().getType());
        assertEquals("4111111111111111", request.getPaymentInformation().getCard().getNumber());
        assertEquals("USD", request.getOrderInformation().getAmountDetails().getCurrency());
        assertEquals("49.90", request.getOrderInformation().getAmountDetails().getTotalAmount());
        assertEquals("ann@example.com", request.getOrderInformation().getBillTo().getEmail());
        assertEquals("203.0.113.7", request.getDeviceInformation().getIpAddress());
        assertEquals("agent", request.getDeviceInformation().getUserAgentBrowserValue());
    }

    @Test
    void derivesTheCybersourceCardTypeFromTheCardNumber() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_FAILED", null));
        ArgumentCaptor<CheckPayerAuthEnrollmentRequest> captor =
                ArgumentCaptor.forClass(CheckPayerAuthEnrollmentRequest.class);

        enrollmentProvider.enroll(command("5555555555554444"));
        enrollmentProvider.enroll(command("2223003122003222"));
        enrollmentProvider.enroll(command("378282246310005"));

        verify(enrollmentClient, org.mockito.Mockito.times(3)).checkEnrollment(captor.capture());
        assertEquals("002", captor.getAllValues().get(0).getPaymentInformation().getCard().getType());
        assertEquals("002", captor.getAllValues().get(1).getPaymentInformation().getCard().getType());
        assertEquals("003", captor.getAllValues().get(2).getPaymentInformation().getCard().getType());
    }

    @Test
    void mapsAFrictionlessSuccess() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_SUCCESSFUL",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation()
                        .cavv("cavv-value").eci("05").xid("xid-value").specificationVersion("2.2.0")
                        .directoryServerTransactionId("ds-tx").authenticationTransactionId("auth-tx")
                        .veresEnrolled("Y")));

        ThreeDSEnrollmentResult result = enrollmentProvider.enroll(command("4111111111111111"));

        assertEquals(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS, result.outcome());
        assertEquals("cavv-value", result.authenticationValue());
        assertEquals("05", result.eci());
        assertEquals("auth-tx", result.authenticationTransactionId());
    }

    @Test
    void usesTheUcafAuthenticationDataWhenThereIsNoCavv() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_SUCCESSFUL",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation()
                        .ucafAuthenticationData("ucaf-value").eci("02")));

        ThreeDSEnrollmentResult result = enrollmentProvider.enroll(command("5555555555554444"));

        assertEquals(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS, result.outcome());
        assertEquals("ucaf-value", result.authenticationValue());
    }

    @Test
    void mapsAChallenge() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "PENDING_AUTHENTICATION",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation()
                        .stepUpUrl("https://step-up.example").accessToken("jwt")
                        .authenticationTransactionId("auth-tx")));

        ThreeDSEnrollmentResult result = enrollmentProvider.enroll(command("4111111111111111"));

        assertEquals(ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED, result.outcome());
        assertEquals("https://step-up.example", result.stepUpUrl());
        assertEquals("jwt", result.accessToken());
    }

    @Test
    void rejectsAnIncompleteChallenge() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "PENDING_AUTHENTICATION",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation().stepUpUrl("https://step-up.example")));

        assertThrows(ThreeDSProviderException.class, () -> enrollmentProvider.enroll(command("4111111111111111")));
    }

    @Test
    void mapsAFailedAuthentication() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse("AUTHENTICATION_FAILED", null));

        assertEquals(ThreeDSEnrollmentOutcome.FAILED,
                enrollmentProvider.enroll(command("4111111111111111")).outcome());
    }

    @Test
    void mapsACardThatIsNotEnrolledToUnavailable() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_SUCCESSFUL",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation().veresEnrolled("N")));

        assertEquals(ThreeDSEnrollmentOutcome.UNAVAILABLE,
                enrollmentProvider.enroll(command("4111111111111111")).outcome());
    }

    @Test
    void rejectsSuccessWithoutAnAuthenticationValueOrNonEnrollment() {
        when(enrollmentClient.checkEnrollment(any())).thenReturn(enrollmentResponse(
                "AUTHENTICATION_SUCCESSFUL",
                new RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation().veresEnrolled("Y")));

        assertThrows(ThreeDSProviderException.class, () -> enrollmentProvider.enroll(command("4111111111111111")));
    }

    @Test
    void rejectsAnUnacceptedOrEmptyEnrollmentResponse() {
        when(enrollmentClient.checkEnrollment(any()))
                .thenReturn(enrollmentResponse("INVALID_REQUEST", null))
                .thenReturn(null);

        assertThrows(ThreeDSProviderException.class, () -> enrollmentProvider.enroll(command("4111111111111111")));
        assertThrows(ThreeDSProviderException.class, () -> enrollmentProvider.enroll(command("4111111111111111")));
    }

    @Test
    void mapsEnrollmentClientErrorsToProviderErrors() {
        when(enrollmentClient.checkEnrollment(any()))
                .thenThrow(new CybersourceClientException("Cybersource error", new RuntimeException()));

        assertThrows(ThreeDSProviderException.class, () -> enrollmentProvider.enroll(command("4111111111111111")));
    }
}
