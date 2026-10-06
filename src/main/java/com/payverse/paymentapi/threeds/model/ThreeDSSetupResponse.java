package com.payverse.paymentapi.threeds.model;

import java.util.UUID;

public record ThreeDSSetupResponse(
        String referenceId,
        String accessToken,
        String deviceDataCollectionUrl,
        UUID paymentId) {

    public ThreeDSSetupResponse(
            String referenceId,
            String accessToken,
            String deviceDataCollectionUrl) {
        this(referenceId, accessToken, deviceDataCollectionUrl, null);
    }

    public ThreeDSSetupResponse withPaymentId(UUID paymentId) {
        return new ThreeDSSetupResponse(referenceId, accessToken, deviceDataCollectionUrl, paymentId);
    }

    @Override
    public String toString() {
        return "ThreeDSSetupResponse[paymentId=" + paymentId
                + ", referenceId=" + referenceId
                + ", accessToken=****, deviceDataCollectionUrl=" + deviceDataCollectionUrl + "]";
    }
}
