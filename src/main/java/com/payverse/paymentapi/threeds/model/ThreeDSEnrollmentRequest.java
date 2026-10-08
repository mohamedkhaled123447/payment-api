package com.payverse.paymentapi.threeds.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ThreeDSEnrollmentRequest(
        @NotNull UUID paymentId,
        @NotNull @Valid ThreeDSCard card,
        @NotNull @Valid ThreeDSBillTo billTo,
        @NotNull @Valid ThreeDSBrowser browser,
        @NotBlank String returnUrl) {

    @Override
    public String toString() {
        return "ThreeDSEnrollmentRequest[paymentId=" + paymentId
                + ", card=" + card + "]";
    }
}
