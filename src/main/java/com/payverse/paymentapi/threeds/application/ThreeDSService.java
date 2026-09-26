package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import org.springframework.stereotype.Service;

@Service
public class ThreeDSService {

    private final ThreeDSProvider threeDSProvider;

    public ThreeDSService(ThreeDSProvider threeDSProvider) {
        this.threeDSProvider = threeDSProvider;
    }

    public ThreeDSSetupResponse setup(ThreeDSSetupRequest request) {
        return threeDSProvider.setup(request);
    }
}
