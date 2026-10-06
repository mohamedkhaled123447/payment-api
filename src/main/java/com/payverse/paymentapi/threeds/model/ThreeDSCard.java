package com.payverse.paymentapi.threeds.model;

import jakarta.validation.constraints.NotBlank;

public record ThreeDSCard(
        @NotBlank String number,
        @NotBlank String expirationMonth,
        @NotBlank String expirationYear) {

    @Override
    public String toString() {
        return "ThreeDSCard[number=****, expirationMonth=" + expirationMonth + ", expirationYear=" + expirationYear + "]";
    }
}
