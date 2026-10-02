package com.payverse.paymentapi.threeds.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreeDSEnrollmentResponse(
        UUID paymentId,
        ThreeDSEnrollmentOutcome outcome,
        String stepUpUrl,
        String accessToken,
        String authenticationTransactionId) {

    @Override
    public String toString() {
        return "ThreeDSEnrollmentResponse[paymentId=" + paymentId
                + ", outcome=" + outcome
                + ", stepUpUrl=" + stepUpUrl
                + ", accessToken=****, authenticationTransactionId=" + authenticationTransactionId + "]";
    }
}
