package com.payverse.paymentapi.threeds.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class ThreeDSProviderException extends RuntimeException {

    public ThreeDSProviderException(String message) {
        super(message);
    }

    public ThreeDSProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
