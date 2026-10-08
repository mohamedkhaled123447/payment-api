package com.payverse.paymentapi.payment.application;

import com.payverse.paymentapi.payment.domain.CaptureMethod;
import com.payverse.paymentapi.payment.domain.Payment;
import com.payverse.paymentapi.payment.persistence.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment create(BigDecimal amount, String currency, CaptureMethod captureMethod, String merchantReference) {
        // Flush so the insert, and the timestamps it sets, happen before the caller builds its response.
        return paymentRepository.saveAndFlush(Payment.create(amount, currency, captureMethod, merchantReference));
    }

    public Payment get(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
