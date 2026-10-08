package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.idempotency.IdempotencyKeyInProgressException;
import com.payverse.paymentapi.idempotency.IdempotencyKeyReusedException;
import com.payverse.paymentapi.idempotency.IdempotencyService;
import com.payverse.paymentapi.idempotency.IdempotentResult;
import com.payverse.paymentapi.idempotency.InvalidIdempotencyKeyException;
import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.application.PaymentService;
import com.payverse.paymentapi.payment.domain.CaptureMethod;
import com.payverse.paymentapi.payment.domain.InvalidPaymentException;
import com.payverse.paymentapi.payment.domain.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String BODY = """
            {"amount": "49.90", "currency": "USD", "captureMethod": "MANUAL", "merchantReference": "order-1042"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private IdempotencyService idempotencyService;

    private final Payment payment = Payment.create(new BigDecimal("49.90"), "USD", CaptureMethod.MANUAL, "order-1042");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void idempotencyRunsTheOperation() {
        when(idempotencyService.execute(anyString(), anyString(), eq(PaymentResponse.class), any()))
                .thenAnswer(invocation -> ((Supplier<IdempotentResult<PaymentResponse>>) invocation.getArgument(3)).get());
        when(paymentService.create(any(), any(), any(), any())).thenReturn(payment);
    }

    private ResultActions create(String key, String body) throws Exception {
        var request = post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    @Test
    void createReturns201WithTheLocationAndTheAmountAsAString() throws Exception {
        create("key-1", BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/" + payment.getId()))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("49.90"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.captureMethod").value("MANUAL"))
                .andExpect(jsonPath("$.merchantReference").value("order-1042"));

        verify(paymentService).create(new BigDecimal("49.90"), "USD", CaptureMethod.MANUAL, "order-1042");
        CreatePaymentRequest parsed = new CreatePaymentRequest(
                new BigDecimal("49.90"), "USD", CaptureMethod.MANUAL, "order-1042");
        verify(idempotencyService).execute(eq("key-1"), eq(parsed.requestHash()), eq(PaymentResponse.class), any());
    }

    @Test
    void aReplayedResponseKeepsItsStatusAndIsMarked() throws Exception {
        when(idempotencyService.execute(anyString(), anyString(), eq(PaymentResponse.class), any()))
                .thenReturn(new IdempotentResult<>(201, PaymentResponse.from(payment), payment.getId(), true));

        create("key-1", BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(payment.getId().toString()));

        verifyNoInteractions(paymentService);
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        create(null, BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The Idempotency-Key header is required"));

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void anInvalidBodyIsRejectedWithFieldErrorsButNoValues() throws Exception {
        create("key-1", "{\"amount\": \"-5\", \"currency\": \"US\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist());
        create("key-1", "{\"amount\": \"abc\"}").andExpect(status().isBadRequest());
        create("key-1", BODY.replace("MANUAL", "LATER")).andExpect(status().isBadRequest());
        create("key-1", BODY.replace("order-1042", "x".repeat(101))).andExpect(status().isBadRequest());

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void idempotencyAndDomainErrorsMapToHttpStatuses() throws Exception {
        when(idempotencyService.execute(anyString(), anyString(), eq(PaymentResponse.class), any()))
                .thenThrow(new InvalidIdempotencyKeyException("Idempotency-Key must not be blank"))
                .thenThrow(new InvalidPaymentException("amount has more decimal places than USD allows (2)"))
                .thenThrow(new IdempotencyKeyReusedException())
                .thenThrow(new IdempotencyKeyInProgressException());

        create("key-1", BODY).andExpect(status().isBadRequest());
        create("key-1", BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("amount has more decimal places than USD allows (2)"));
        create("key-1", BODY).andExpect(status().isUnprocessableContent());
        create("key-1", BODY).andExpect(status().isConflict());
    }

    @Test
    void getReturnsThePayment() throws Exception {
        when(paymentService.get(payment.getId())).thenReturn(payment);

        mockMvc.perform(get("/api/v1/payments/" + payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value("49.90"));
    }

    @Test
    void getMapsAnUnknownOrMalformedIdTo404Or400() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(paymentService.get(unknown)).thenThrow(new PaymentNotFoundException(unknown));

        mockMvc.perform(get("/api/v1/payments/" + unknown)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/payments/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
