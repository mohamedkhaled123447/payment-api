package com.payverse.paymentapi.threeds.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ThreeDSValidationCommand(
        UUID paymentId,
        String authenticationTransactionId,
        ThreeDSCard card,
        BigDecimal amount,
        String currency) {

    @Override
    public String toString() {
        return "ThreeDSValidationCommand[paymentId=" + paymentId
                + ", authenticationTransactionId=" + authenticationTransactionId
                + ", card=" + card
                + ", amount=" + amount
                + ", currency=" + currency + "]";
    }
}
