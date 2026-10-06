package com.payverse.paymentapi.threeds.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ThreeDSEnrollmentCommand(
        UUID paymentId,
        String referenceId,
        ThreeDSCard card,
        BigDecimal amount,
        String currency,
        ThreeDSBillTo billTo,
        ThreeDSBrowser browser,
        String ipAddress,
        String returnUrl) {

    @Override
    public String toString() {
        return "ThreeDSEnrollmentCommand[paymentId=" + paymentId
                + ", referenceId=" + referenceId
                + ", card=" + card
                + ", amount=" + amount
                + ", currency=" + currency + "]";
    }
}
