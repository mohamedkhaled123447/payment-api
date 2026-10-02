package com.payverse.paymentapi.threeds.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record ThreeDSEnrollmentRequest(
        @NotNull UUID paymentId,
        @NotNull @Valid ThreeDSCard card,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull @Valid ThreeDSBillTo billTo,
        @NotNull @Valid ThreeDSBrowser browser,
        @NotBlank String returnUrl) {

    @Override
    public String toString() {
        return "ThreeDSEnrollmentRequest[paymentId=" + paymentId
                + ", card=" + card
                + ", amount=" + amount
                + ", currency=" + currency + "]";
    }
}
