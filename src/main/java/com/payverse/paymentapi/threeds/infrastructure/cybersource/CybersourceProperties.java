package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cybersource")
public record CybersourceProperties(
        @NotBlank @Pattern(regexp = SET, message = UNSET) String merchantId,
        @NotBlank @Pattern(regexp = SET, message = UNSET) String merchantKeyId,
        @NotBlank @Pattern(regexp = SET, message = UNSET) String merchantSecretKey,
        @NotBlank @Pattern(regexp = SET, message = UNSET) String runEnvironment) {

    // Spring binds an unresolved "${VARIABLE}" placeholder as literal text, so reject it explicitly.
    private static final String SET = "^(?!\\$\\{)[\\s\\S]*$";
    private static final String UNSET = "must be set (the environment variable is missing)";
}
