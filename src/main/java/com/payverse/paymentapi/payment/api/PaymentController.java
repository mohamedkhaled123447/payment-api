package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.idempotency.IdempotencyService;
import com.payverse.paymentapi.idempotency.IdempotentResult;
import com.payverse.paymentapi.payment.application.PaymentService;
import com.payverse.paymentapi.payment.domain.Payment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(PaymentController.BASE_PATH)
public class PaymentController {

    static final String BASE_PATH = "/api/v1/payments";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    // Idempotency lives here, at the HTTP boundary: it is about replaying an HTTP response
    // (status and body) to a client that retried, so the service stays free of HTTP concerns.
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        IdempotentResult<PaymentResponse> result = idempotencyService.execute(
                idempotencyKey, request.requestHash(), PaymentResponse.class, () -> {
                    Payment payment = paymentService.create(
                            request.amount(), request.currency(), request.captureMethod(), request.merchantReference());
                    return IdempotentResult.of(HttpStatus.CREATED.value(), PaymentResponse.from(payment), payment.getId());
                });

        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .location(URI.create(BASE_PATH + "/" + result.body().id()));
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(result.body());
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.get(paymentId));
    }
}
