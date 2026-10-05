package com.payverse.paymentapi.threeds.api;

import com.payverse.paymentapi.threeds.application.ThreeDSService;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/3ds")
public class ThreeDSController {

    private final ThreeDSService threeDSService;

    public ThreeDSController(ThreeDSService threeDSService) {
        this.threeDSService = threeDSService;
    }

    @PostMapping("/setup")
    public ResponseEntity<ThreeDSSetupResponse> setup(@Valid @RequestBody ThreeDSSetupRequest request) {
        return ResponseEntity.ok(threeDSService.setup(request));
    }

    @PostMapping("/enrollment")
    public ResponseEntity<ThreeDSEnrollmentResponse> enroll(
            @Valid @RequestBody ThreeDSEnrollmentRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(threeDSService.enroll(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/validation")
    public ResponseEntity<ThreeDSValidationResponse> validate(@Valid @RequestBody ThreeDSValidationRequest request) {
        return ResponseEntity.ok(threeDSService.validate(request));
    }
}
