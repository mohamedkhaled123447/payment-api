package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;

import java.util.UUID;

public class ThreeDSInvalidStateException extends RuntimeException {

    public ThreeDSInvalidStateException(UUID paymentId, ThreeDSSessionStatus status) {
        super("3DS session for payment " + paymentId + " is " + status + " and cannot be enrolled");
    }
}
