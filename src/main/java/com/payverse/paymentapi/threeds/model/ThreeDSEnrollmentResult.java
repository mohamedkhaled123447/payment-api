package com.payverse.paymentapi.threeds.model;

public record ThreeDSEnrollmentResult(
        ThreeDSEnrollmentOutcome outcome,
        String authenticationTransactionId,
        String stepUpUrl,
        String accessToken,
        String authenticationValue,
        String eci,
        String ecommerceIndicator,
        String xid,
        String specificationVersion,
        String directoryServerTransactionId,
        String veresEnrolled) {

    @Override
    public String toString() {
        return "ThreeDSEnrollmentResult[outcome=" + outcome
                + ", authenticationTransactionId=" + authenticationTransactionId
                + ", eci=" + eci
                + ", veresEnrolled=" + veresEnrolled
                + ", accessToken=****, authenticationValue=****]";
    }
}
