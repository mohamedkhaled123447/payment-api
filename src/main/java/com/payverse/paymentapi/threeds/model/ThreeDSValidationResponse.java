package com.payverse.paymentapi.threeds.model;

import java.util.UUID;

public record ThreeDSValidationResponse(
        UUID paymentId,
        ThreeDSValidationOutcome outcome) {
}
