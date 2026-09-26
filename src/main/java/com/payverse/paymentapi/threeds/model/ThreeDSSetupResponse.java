package com.payverse.paymentapi.threeds.model;

public record ThreeDSSetupResponse(
        String referenceId,
        String accessToken,
        String deviceDataCollectionUrl) {

    @Override
    public String toString() {
        return "ThreeDSSetupResponse[referenceId=" + referenceId
                + ", accessToken=****, deviceDataCollectionUrl=" + deviceDataCollectionUrl + "]";
    }
}
