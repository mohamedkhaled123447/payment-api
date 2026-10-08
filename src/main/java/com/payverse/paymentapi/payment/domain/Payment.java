package com.payverse.paymentapi.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * A payment and its state machine. State only changes through the methods below, which reject
 * transitions that {@link PaymentStatus} does not allow.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    // Matches NUMERIC(19, 4): at most 15 digits before the decimal point.
    private static final int MAX_INTEGER_DIGITS = 15;

    // Assigned here rather than by the database: the id is our reference with the provider and
    // must be known before anything is sent (D4).
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false, length = 20)
    private CaptureMethod captureMethod;

    @Column(name = "merchant_reference", length = 100)
    private String merchantReference;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Payment create(
            BigDecimal amount, String currencyCode, CaptureMethod captureMethod, String merchantReference) {
        if (captureMethod == null) {
            throw new InvalidPaymentException("captureMethod is required");
        }
        Currency currency = currency(currencyCode);
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.status = PaymentStatus.CREATED;
        payment.amount = amount(amount, currency);
        payment.currency = currency.getCurrencyCode();
        payment.captureMethod = captureMethod;
        payment.merchantReference = merchantReference;
        return payment;
    }

    /** The amount with exactly the currency's minor-unit digits, e.g. 49.90 USD or 1000 JPY. */
    public BigDecimal getAmount() {
        // The column has scale 4, so a loaded value is e.g. 49.9000. The digits beyond the
        // currency's minor units are always zero (see amount()), so this never rounds.
        return amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    /** Fails if 3DS can't start for this payment, without changing it. */
    public void ensureCanStartThreeDS() {
        checkTransition(PaymentStatus.THREE_DS_PENDING);
    }

    public void startThreeDS() {
        transitionTo(PaymentStatus.THREE_DS_PENDING);
    }

    private void transitionTo(PaymentStatus next) {
        checkTransition(next);
        status = next;
    }

    private void checkTransition(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new PaymentInvalidStateException(id, status, next);
        }
    }

    private static Currency currency(String code) {
        if (code == null) {
            throw new InvalidPaymentException("currency is required");
        }
        Currency currency;
        try {
            currency = Currency.getInstance(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentException("currency " + code + " is not a known ISO 4217 code");
        }
        // Codes such as XAU (gold) or XXX have no minor units and are not payable currencies.
        if (currency.getDefaultFractionDigits() < 0) {
            throw new InvalidPaymentException("currency " + code + " can't be used for payments");
        }
        return currency;
    }

    private static BigDecimal amount(BigDecimal amount, Currency currency) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPaymentException("amount must be greater than zero");
        }
        int digits = currency.getDefaultFractionDigits();
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > digits) {
            throw new InvalidPaymentException("amount has more decimal places than "
                    + currency.getCurrencyCode() + " allows (" + digits + ")");
        }
        if (stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidPaymentException("amount is too large");
        }
        return amount.setScale(digits, RoundingMode.UNNECESSARY);
    }
}
