package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CybersourcePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsConfiguredValues() {
        CybersourceProperties properties =
                new CybersourceProperties("merchant", "key-id", "c2VjcmV0", "apitest.cybersource.com");

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void rejectsBlankValues() {
        CybersourceProperties properties = new CybersourceProperties("", "key-id", "c2VjcmV0", "apitest.cybersource.com");

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("merchantId")));
    }

    @Test
    void rejectsAnUnresolvedPlaceholder() {
        CybersourceProperties properties = new CybersourceProperties(
                "${CYBERSOURCE_MERCHANT_ID}", "key-id", "${CYBERSOURCE_MERCHANT_SECRET_KEY}", "apitest.cybersource.com");

        var violations = validator.validate(properties);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().allMatch(v -> v.getMessage().contains("environment variable is missing")));
    }
}
