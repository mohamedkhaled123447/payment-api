package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.persistence.ThreeDSProviderType;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSession;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionRepository;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ThreeDSService {

    private final ThreeDSProvider threeDSProvider;
    private final ThreeDSSessionRepository threeDSSessionRepository;

    public ThreeDSService(
            ThreeDSProvider threeDSProvider,
            ThreeDSSessionRepository threeDSSessionRepository) {
        this.threeDSProvider = threeDSProvider;
        this.threeDSSessionRepository = threeDSSessionRepository;
    }

    public ThreeDSSetupResponse setup(ThreeDSSetupRequest request) {
        ThreeDSSetupResponse response = threeDSProvider.setup(request);
        UUID paymentId = UUID.randomUUID();
        ThreeDSSession session = new ThreeDSSession();
        session.setPaymentId(paymentId);
        session.setProvider(ThreeDSProviderType.CYBERSOURCE);
        session.setStatus(ThreeDSSessionStatus.SETUP_COMPLETED);
        session.setProviderReferenceId(response.referenceId());
        threeDSSessionRepository.save(session);

        return response.withPaymentId(paymentId);
    }

    public ThreeDSEnrollmentResponse enroll(ThreeDSEnrollmentRequest request, String ipAddress) {
        ThreeDSSession session = threeDSSessionRepository.findByPaymentId(request.paymentId())
                .orElseThrow(() -> new ThreeDSSessionNotFoundException(request.paymentId()));
        if (session.getStatus() != ThreeDSSessionStatus.SETUP_COMPLETED) {
            throw new ThreeDSInvalidStateException(request.paymentId(), session.getStatus());
        }

        // The provider call runs outside any DB transaction; @Version on the session
        // rejects a concurrent enrollment of the same payment when we save below.
        ThreeDSEnrollmentResult result = threeDSProvider.enroll(new ThreeDSEnrollmentCommand(
                request.paymentId(),
                session.getProviderReferenceId(),
                request.card(),
                request.amount(),
                request.currency().toUpperCase(),
                request.billTo(),
                request.browser(),
                ipAddress,
                request.returnUrl()));

        session.setStatus(statusFor(result.outcome()));
        session.setAmount(request.amount());
        session.setCurrency(request.currency().toUpperCase());
        session.setAuthenticationTransactionId(result.authenticationTransactionId());
        session.setAuthenticationValue(result.authenticationValue());
        session.setEci(result.eci());
        session.setEcommerceIndicator(result.ecommerceIndicator());
        session.setXid(result.xid());
        session.setSpecificationVersion(result.specificationVersion());
        session.setDirectoryServerTransactionId(result.directoryServerTransactionId());
        session.setVeresEnrolled(result.veresEnrolled());
        threeDSSessionRepository.save(session);

        boolean challenge = result.outcome() == ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED;
        return new ThreeDSEnrollmentResponse(
                request.paymentId(),
                result.outcome(),
                challenge ? result.stepUpUrl() : null,
                challenge ? result.accessToken() : null,
                challenge ? result.authenticationTransactionId() : null);
    }

    private ThreeDSSessionStatus statusFor(ThreeDSEnrollmentOutcome outcome) {
        return switch (outcome) {
            case FRICTIONLESS_SUCCESS -> ThreeDSSessionStatus.AUTHENTICATED;
            case CHALLENGE_REQUIRED -> ThreeDSSessionStatus.CHALLENGE_REQUIRED;
            case FAILED -> ThreeDSSessionStatus.AUTHENTICATION_FAILED;
            case UNAVAILABLE -> ThreeDSSessionStatus.AUTHENTICATION_UNAVAILABLE;
        };
    }
}
