package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.domain.Payment;
import com.payverse.paymentapi.payment.persistence.PaymentRepository;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResult;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationCommand;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResult;
import com.payverse.paymentapi.threeds.persistence.ThreeDSProviderType;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSession;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionRepository;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ThreeDSService {

    private final ThreeDSProvider threeDSProvider;
    private final ThreeDSSessionRepository threeDSSessionRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration validationClaimTimeout;

    public ThreeDSService(
            ThreeDSProvider threeDSProvider,
            ThreeDSSessionRepository threeDSSessionRepository,
            PaymentRepository paymentRepository,
            TransactionTemplate transactionTemplate,
            @Value("${threeds.validation-claim-timeout:PT2M}") Duration validationClaimTimeout) {
        this.threeDSProvider = threeDSProvider;
        this.threeDSSessionRepository = threeDSSessionRepository;
        this.paymentRepository = paymentRepository;
        this.transactionTemplate = transactionTemplate;
        this.validationClaimTimeout = validationClaimTimeout;
    }

    public ThreeDSSetupResponse setup(ThreeDSSetupRequest request) {
        Payment payment = findPayment(request.paymentId());
        // Reject before calling the provider; the payment itself changes only once setup succeeded.
        payment.ensureCanStartThreeDS();

        ThreeDSSetupResponse response = threeDSProvider.setup(request);

        ThreeDSSession session = new ThreeDSSession();
        session.setPaymentId(payment.getId());
        session.setProvider(ThreeDSProviderType.CYBERSOURCE);
        session.setStatus(ThreeDSSessionStatus.SETUP_COMPLETED);
        session.setProviderReferenceId(response.referenceId());
        // One transaction, so a payment is never THREE_DS_PENDING without its session. The payment's
        // @Version rejects a concurrent setup of the same payment.
        transactionTemplate.executeWithoutResult(status -> {
            payment.startThreeDS();
            paymentRepository.save(payment);
            threeDSSessionRepository.save(session);
        });

        return response.withPaymentId(payment.getId());
    }

    public ThreeDSEnrollmentResponse enroll(ThreeDSEnrollmentRequest request, String ipAddress) {
        ThreeDSSession session = threeDSSessionRepository.findByPaymentId(request.paymentId())
                .orElseThrow(() -> new ThreeDSSessionNotFoundException(request.paymentId()));
        if (session.getStatus() != ThreeDSSessionStatus.SETUP_COMPLETED) {
            throw new ThreeDSInvalidStateException(request.paymentId(), session.getStatus());
        }
        // The payment owns amount and currency (D7); the client can't authenticate a different amount.
        Payment payment = findPayment(session.getPaymentId());

        // The provider call runs outside any DB transaction; @Version on the session
        // rejects a concurrent enrollment of the same payment when we save below.
        ThreeDSEnrollmentResult result = threeDSProvider.enroll(new ThreeDSEnrollmentCommand(
                request.paymentId(),
                session.getProviderReferenceId(),
                request.card(),
                payment.getAmount(),
                payment.getCurrency(),
                request.billTo(),
                request.browser(),
                ipAddress,
                request.returnUrl()));

        session.setStatus(statusFor(result.outcome()));
        // Kept on the session as a record of the amount that was authenticated.
        session.setAmount(payment.getAmount());
        session.setCurrency(payment.getCurrency());
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

    public ThreeDSValidationResponse validate(ThreeDSValidationRequest request) {
        ThreeDSSession session = threeDSSessionRepository.findByPaymentId(request.paymentId())
                .orElseThrow(() -> new ThreeDSSessionNotFoundException(request.paymentId()));

        // A finished validation is final: repeat the stored answer instead of asking the provider again.
        if (session.getStatus() == ThreeDSSessionStatus.AUTHENTICATED) {
            return new ThreeDSValidationResponse(request.paymentId(), ThreeDSValidationOutcome.AUTHENTICATED);
        }
        if (session.getStatus() == ThreeDSSessionStatus.AUTHENTICATION_FAILED) {
            return new ThreeDSValidationResponse(request.paymentId(), ThreeDSValidationOutcome.FAILED);
        }
        if (session.getStatus() == ThreeDSSessionStatus.VALIDATING) {
            if (!claimExpired(session)) {
                throw invalidState(session);
            }
            // The previous attempt never finished. Release it with a real change first, so that the
            // claim below is a version-checked write and only one concurrent request can win it.
            session.setStatus(ThreeDSSessionStatus.CHALLENGE_REQUIRED);
            session = threeDSSessionRepository.save(session);
        }
        if (session.getStatus() != ThreeDSSessionStatus.CHALLENGE_REQUIRED) {
            throw invalidState(session);
        }

        // Claim the session before calling the provider. @Version rejects the save of a concurrent
        // request that read the same row, so only one request reaches the provider.
        session.setStatus(ThreeDSSessionStatus.VALIDATING);
        session = threeDSSessionRepository.save(session);

        // The provider call runs outside any DB transaction. Use the entity returned by save(): it
        // carries the new @Version that the next save must be checked against.
        ThreeDSValidationResult result;
        try {
            result = threeDSProvider.validate(new ThreeDSValidationCommand(
                    request.paymentId(),
                    session.getAuthenticationTransactionId(),
                    request.card(),
                    session.getAmount(),
                    session.getCurrency()));
        } catch (ThreeDSProviderException exception) {
            releaseClaim(session, exception);
            throw exception;
        }

        applyValidationResult(session, result);
        threeDSSessionRepository.save(session);

        return new ThreeDSValidationResponse(request.paymentId(), result.outcome());
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private boolean claimExpired(ThreeDSSession session) {
        return session.getUpdatedAt() != null
                && session.getUpdatedAt().plus(validationClaimTimeout).isBefore(Instant.now());
    }

    private ThreeDSInvalidStateException invalidState(ThreeDSSession session) {
        return new ThreeDSInvalidStateException(session.getPaymentId(), session.getStatus(), "validated");
    }

    // Give the claim back so the customer can retry. If even this fails, the claim timeout
    // makes the session reclaimable, so the original provider failure is the error to report.
    private void releaseClaim(ThreeDSSession session, ThreeDSProviderException cause) {
        try {
            session.setStatus(ThreeDSSessionStatus.CHALLENGE_REQUIRED);
            threeDSSessionRepository.save(session);
        } catch (RuntimeException releaseFailure) {
            cause.addSuppressed(releaseFailure);
        }
    }

    private void applyValidationResult(ThreeDSSession session, ThreeDSValidationResult result) {
        if (result.outcome() != ThreeDSValidationOutcome.AUTHENTICATED) {
            session.setStatus(ThreeDSSessionStatus.AUTHENTICATION_FAILED);
            return;
        }
        session.setStatus(ThreeDSSessionStatus.AUTHENTICATED);
        session.setAuthenticationValue(result.authenticationValue());
        session.setEci(result.eci());
        session.setXid(result.xid());
        // Enrollment already stored these; keep them unless validation returns a newer value.
        if (result.ecommerceIndicator() != null) {
            session.setEcommerceIndicator(result.ecommerceIndicator());
        }
        if (result.specificationVersion() != null) {
            session.setSpecificationVersion(result.specificationVersion());
        }
        if (result.directoryServerTransactionId() != null) {
            session.setDirectoryServerTransactionId(result.directoryServerTransactionId());
        }
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
