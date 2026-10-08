package com.payverse.paymentapi.payment.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.payverse.paymentapi.payment.domain.PaymentStatus.AUTHORIZED;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.AUTHORIZING;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.CAPTURED;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.CREATED;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.DECLINED;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.FAILED;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.PENDING_REVIEW;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.THREE_DS_PENDING;
import static com.payverse.paymentapi.payment.domain.PaymentStatus.VOIDED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentStatusTest {

    // The state machine from docs/design/payments.md, section 3. Every pair not listed is illegal.
    private static final Map<PaymentStatus, Set<PaymentStatus>> DESIGN = Map.of(
            CREATED, EnumSet.of(THREE_DS_PENDING, AUTHORIZING),
            THREE_DS_PENDING, EnumSet.of(AUTHORIZING),
            AUTHORIZING, EnumSet.of(AUTHORIZED, PENDING_REVIEW, DECLINED, FAILED, CAPTURED),
            PENDING_REVIEW, EnumSet.of(AUTHORIZED, DECLINED),
            AUTHORIZED, EnumSet.of(CAPTURED, VOIDED),
            CAPTURED, EnumSet.noneOf(PaymentStatus.class),
            VOIDED, EnumSet.noneOf(PaymentStatus.class),
            DECLINED, EnumSet.noneOf(PaymentStatus.class),
            FAILED, EnumSet.noneOf(PaymentStatus.class));

    @Test
    void everyTransitionMatchesTheDesign() {
        assertEquals(EnumSet.allOf(PaymentStatus.class), EnumSet.copyOf(DESIGN.keySet()));
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                assertEquals(DESIGN.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void onlyStatesWithoutTransitionsAreFinal() {
        assertEquals(EnumSet.of(CAPTURED, VOIDED, DECLINED, FAILED),
                EnumSet.copyOf(java.util.Arrays.stream(PaymentStatus.values()).filter(PaymentStatus::isFinal).toList()));
    }
}
