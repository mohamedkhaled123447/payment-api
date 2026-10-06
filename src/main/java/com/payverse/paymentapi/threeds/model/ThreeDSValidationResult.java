package com.payverse.paymentapi.threeds.model;

public record ThreeDSValidationResult(
        ThreeDSValidationOutcome outcome,
        String authenticationValue,
        String eci,
        String ecommerceIndicator,
        String xid,
        String specificationVersion,
        String directoryServerTransactionId) {

    @Override
    public String toString() {
        return "ThreeDSValidationResult[outcome=" + outcome
                + ", eci=" + eci
                + ", authenticationValue=****]";
    }
}
