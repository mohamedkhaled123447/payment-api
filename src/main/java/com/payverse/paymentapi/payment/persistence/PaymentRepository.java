package com.payverse.paymentapi.payment.persistence;

import com.payverse.paymentapi.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
