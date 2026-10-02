package com.payverse.paymentapi.threeds.persistence;

public enum ThreeDSSessionStatus {
    SETUP_COMPLETED,
    CHALLENGE_REQUIRED,
    AUTHENTICATED,
    AUTHENTICATION_FAILED,
    AUTHENTICATION_UNAVAILABLE
}
