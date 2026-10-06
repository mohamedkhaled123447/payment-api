package com.payverse.paymentapi.threeds.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ThreeDSBrowser(
        @NotBlank String acceptHeader,
        @NotBlank String userAgent,
        @NotBlank String language,
        @NotNull Boolean javaEnabled,
        @NotBlank String colorDepth,
        @NotBlank String screenHeight,
        @NotBlank String screenWidth,
        @NotBlank String timeZoneOffset) {
}
