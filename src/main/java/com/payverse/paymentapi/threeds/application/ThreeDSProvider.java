package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;

public interface ThreeDSProvider {

    ThreeDSSetupResponse setup(ThreeDSSetupRequest request);

    ThreeDSEnrollmentResult enroll(ThreeDSEnrollmentCommand command);
}
