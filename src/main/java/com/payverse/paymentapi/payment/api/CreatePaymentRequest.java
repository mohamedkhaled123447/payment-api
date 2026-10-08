package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.idempotency.RequestHashes;
import com.payverse.paymentapi.payment.domain.CaptureMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Amount and currency rules that depend on the currency (minor-unit digits, known ISO codes) are
 * checked by {@link com.payverse.paymentapi.payment.domain.Payment#create}.
 */
public record CreatePaymentRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull CaptureMethod captureMethod,
        @Size(max = 100) String merchantReference) {

    /**
     * Hash of the request's meaning rather than its bytes (D10): "49.9" and "49.90", or "usd" and
     * "USD", are the same request.
     */
    public String requestHash() {
        return RequestHashes.sha256(String.join("\n",
                amount.stripTrailingZeros().toPlainString(),
                currency.toUpperCase(Locale.ROOT),
                captureMethod.name(),
                merchantReference == null ? "" : merchantReference));
    }
}
