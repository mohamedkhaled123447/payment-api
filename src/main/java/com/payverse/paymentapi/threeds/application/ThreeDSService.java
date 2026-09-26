package com.payverse.paymentapi.threeds.application;

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
}
