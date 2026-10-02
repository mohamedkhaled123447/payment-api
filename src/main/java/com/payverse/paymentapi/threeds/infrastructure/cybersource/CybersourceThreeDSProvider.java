package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Model.CheckPayerAuthEnrollmentRequest;
import Model.PayerAuthSetupRequest;
import Model.RiskV1AuthenticationSetupsPost201Response;
import Model.RiskV1AuthenticationSetupsPost201ResponseConsumerAuthenticationInformation;
import Model.RiskV1AuthenticationsPost201Response;
import Model.RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation;
import Model.Riskv1authenticationsDeviceInformation;
import Model.Riskv1authenticationsOrderInformation;
import Model.Riskv1authenticationsOrderInformationAmountDetails;
import Model.Riskv1authenticationsOrderInformationBillTo;
import Model.Riskv1authenticationsPaymentInformation;
import Model.Riskv1authenticationsetupsPaymentInformation;
import Model.Riskv1authenticationsetupsPaymentInformationCard;
import Model.Riskv1decisionsClientReferenceInformation;
import Model.Riskv1decisionsConsumerAuthenticationInformation;
import com.payverse.paymentapi.threeds.application.ThreeDSProvider;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.model.ThreeDSBillTo;
import com.payverse.paymentapi.threeds.model.ThreeDSBrowser;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
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

    @Override
    public ThreeDSEnrollmentResult enroll(ThreeDSEnrollmentCommand command) {
        try {
            RiskV1AuthenticationsPost201Response response =
                    cybersourceClient.checkEnrollment(toEnrollmentRequest(command));
            return toEnrollmentResult(response);
        } catch (CybersourceClientException exception) {
            throw new ThreeDSProviderException("3DS enrollment check failed", exception);
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

    private CheckPayerAuthEnrollmentRequest toEnrollmentRequest(ThreeDSEnrollmentCommand command) {
        Riskv1authenticationsetupsPaymentInformationCard card = new Riskv1authenticationsetupsPaymentInformationCard()
                .type(cardType(command.card().number()))
                .number(command.card().number())
                .expirationMonth(command.card().expirationMonth())
                .expirationYear(command.card().expirationYear());

        ThreeDSBillTo billTo = command.billTo();
        Riskv1authenticationsOrderInformation orderInformation = new Riskv1authenticationsOrderInformation()
                .amountDetails(new Riskv1authenticationsOrderInformationAmountDetails()
                        .currency(command.currency())
                        .totalAmount(command.amount().toPlainString()))
                .billTo(new Riskv1authenticationsOrderInformationBillTo()
                        .firstName(billTo.firstName())
                        .lastName(billTo.lastName())
                        .email(billTo.email())
                        .address1(billTo.address1())
                        .locality(billTo.locality())
                        .administrativeArea(billTo.administrativeArea())
                        .postalCode(billTo.postalCode())
                        .country(billTo.country()));

        ThreeDSBrowser browser = command.browser();
        Riskv1authenticationsDeviceInformation deviceInformation = new Riskv1authenticationsDeviceInformation()
                .ipAddress(command.ipAddress())
                .httpAcceptBrowserValue(browser.acceptHeader())
                .httpAcceptContent(browser.acceptHeader())
                .httpBrowserLanguage(browser.language())
                .httpBrowserJavaEnabled(browser.javaEnabled())
                .httpBrowserJavaScriptEnabled(true)
                .httpBrowserColorDepth(browser.colorDepth())
                .httpBrowserScreenHeight(browser.screenHeight())
                .httpBrowserScreenWidth(browser.screenWidth())
                .httpBrowserTimeDifference(browser.timeZoneOffset())
                .userAgentBrowserValue(browser.userAgent());

        return new CheckPayerAuthEnrollmentRequest()
                .clientReferenceInformation(new Riskv1decisionsClientReferenceInformation()
                        .code(command.paymentId().toString()))
                .paymentInformation(new Riskv1authenticationsPaymentInformation().card(card))
                .orderInformation(orderInformation)
                .deviceInformation(deviceInformation)
                .consumerAuthenticationInformation(new Riskv1decisionsConsumerAuthenticationInformation()
                        .referenceId(command.referenceId())
                        .returnUrl(command.returnUrl())
                        .deviceChannel("BROWSER")
                        .transactionMode("S"));
    }

    private ThreeDSEnrollmentResult toEnrollmentResult(RiskV1AuthenticationsPost201Response response) {
        if (response == null || response.getStatus() == null) {
            throw new ThreeDSProviderException("Cybersource enrollment response is incomplete");
        }
        RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation info =
                response.getConsumerAuthenticationInformation();

        return switch (response.getStatus()) {
            case "PENDING_AUTHENTICATION" -> {
                if (info == null
                        || info.getStepUpUrl() == null
                        || info.getAccessToken() == null
                        || info.getAuthenticationTransactionId() == null) {
                    throw new ThreeDSProviderException("Cybersource challenge response is incomplete");
                }
                yield enrollmentResult(ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED, info);
            }
            case "AUTHENTICATION_SUCCESSFUL" -> {
                if (info == null) {
                    throw new ThreeDSProviderException("Cybersource enrollment response is incomplete");
                }
                if (authenticationValue(info) != null) {
                    yield enrollmentResult(ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS, info);
                }
                if (isNotEnrolled(info.getVeresEnrolled())) {
                    yield enrollmentResult(ThreeDSEnrollmentOutcome.UNAVAILABLE, info);
                }
                throw new ThreeDSProviderException("Cybersource enrollment response is incomplete");
            }
            case "AUTHENTICATION_FAILED" -> enrollmentResult(ThreeDSEnrollmentOutcome.FAILED, info);
            default -> throw new ThreeDSProviderException("Cybersource enrollment request was not accepted");
        };
    }

    private ThreeDSEnrollmentResult enrollmentResult(
            ThreeDSEnrollmentOutcome outcome,
            RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation info) {
        if (info == null) {
            return new ThreeDSEnrollmentResult(outcome, null, null, null, null, null, null, null, null, null, null);
        }
        return new ThreeDSEnrollmentResult(
                outcome,
                info.getAuthenticationTransactionId(),
                info.getStepUpUrl(),
                info.getAccessToken(),
                authenticationValue(info),
                info.getEci(),
                info.getEcommerceIndicator(),
                info.getXid(),
                info.getSpecificationVersion(),
                info.getDirectoryServerTransactionId(),
                info.getVeresEnrolled());
    }

    // Visa/Amex return a CAVV; Mastercard returns the UCAF authentication data instead.
    private String authenticationValue(RiskV1DecisionsPost201ResponseConsumerAuthenticationInformation info) {
        return info.getCavv() != null ? info.getCavv() : info.getUcafAuthenticationData();
    }

    private boolean isNotEnrolled(String veresEnrolled) {
        return "N".equals(veresEnrolled) || "U".equals(veresEnrolled) || "B".equals(veresEnrolled);
    }

    private String cardType(String number) {
        if (number.startsWith("4")) {
            return "001";
        }
        if (number.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720).*")) {
            return "002";
        }
        if (number.startsWith("34") || number.startsWith("37")) {
            return "003";
        }
        return null;
    }
}
