package com.payverse.paymentapi.threeds.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ThreeDSValidationRequest(
        @NotNull UUID paymentId,
        @NotNull @Valid ThreeDSCard card) {

    @Override
    public String toString() {
        return "ThreeDSValidationRequest[paymentId=" + paymentId + ", card=" + card + "]";
    }
}
