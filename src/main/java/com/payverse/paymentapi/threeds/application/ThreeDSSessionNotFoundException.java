package com.payverse.paymentapi.threeds.application;

import java.util.UUID;

public class ThreeDSSessionNotFoundException extends RuntimeException {

    public ThreeDSSessionNotFoundException(UUID paymentId) {
        super("No 3DS session found for payment " + paymentId);
    }
}
