package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cybersource")
public record CybersourceProperties(
        @NotBlank String merchantId,
        @NotBlank String merchantKeyId,
        @NotBlank String merchantSecretKey,
        @NotBlank String runEnvironment) {
}
