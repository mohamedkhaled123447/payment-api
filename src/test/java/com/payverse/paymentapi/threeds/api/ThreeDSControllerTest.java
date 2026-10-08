package com.payverse.paymentapi.threeds.api;

import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.domain.PaymentInvalidStateException;
import com.payverse.paymentapi.payment.domain.PaymentStatus;
import com.payverse.paymentapi.threeds.application.ThreeDSInvalidStateException;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.application.ThreeDSService;
import com.payverse.paymentapi.threeds.application.ThreeDSSessionNotFoundException;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSValidationResponse;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ThreeDSController.class)
class ThreeDSControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("7f0c1f5e-0000-4000-8000-000000000001");

    private static final String VALID_BODY = """
            {
              "paymentId": "7f0c1f5e-0000-4000-8000-000000000001",
              "card": {"number": "4111111111111111", "expirationMonth": "12", "expirationYear": "2028"},
              "billTo": {"firstName": "Ann", "lastName": "Lee", "email": "ann@example.com",
                         "address1": "1 Main St", "locality": "Austin", "administrativeArea": "TX",
                         "postalCode": "73301", "country": "US"},
              "browser": {"acceptHeader": "text/html", "userAgent": "agent", "language": "en-US",
                          "javaEnabled": false, "colorDepth": "24", "screenHeight": "1080",
                          "screenWidth": "1920", "timeZoneOffset": "300"},
              "returnUrl": "https://shop.example/3ds/return"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ThreeDSService threeDSService;

    private org.springframework.test.web.servlet.ResultActions enroll(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/3ds/enrollment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void enrollReturnsTheChallengeData() throws Exception {
        when(threeDSService.enroll(any(), any())).thenReturn(new ThreeDSEnrollmentResponse(
                PAYMENT_ID, ThreeDSEnrollmentOutcome.CHALLENGE_REQUIRED,
                "https://step-up.example", "jwt", "auth-tx"));

        enroll(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CHALLENGE_REQUIRED"))
                .andExpect(jsonPath("$.stepUpUrl").value("https://step-up.example"))
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @Test
    void enrollOmitsStepUpFieldsForAFrictionlessResult() throws Exception {
        when(threeDSService.enroll(any(), any())).thenReturn(new ThreeDSEnrollmentResponse(
                PAYMENT_ID, ThreeDSEnrollmentOutcome.FRICTIONLESS_SUCCESS, null, null, null));

        enroll(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FRICTIONLESS_SUCCESS"))
                .andExpect(jsonPath("$.stepUpUrl").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void enrollRejectsAnInvalidBody() throws Exception {
        enroll("{}").andExpect(status().isBadRequest());
        enroll(VALID_BODY.replace("\"https://shop.example/3ds/return\"", "\"\"")).andExpect(status().isBadRequest());
        enroll(VALID_BODY.replace("\"Ann\"", "null")).andExpect(status().isBadRequest());
    }

    @Test
    void enrollMapsDomainErrorsToHttpStatuses() throws Exception {
        when(threeDSService.enroll(any(), any()))
                .thenThrow(new ThreeDSSessionNotFoundException(PAYMENT_ID))
                .thenThrow(new PaymentNotFoundException(PAYMENT_ID))
                .thenThrow(new ThreeDSInvalidStateException(PAYMENT_ID, ThreeDSSessionStatus.AUTHENTICATED))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenThrow(new ThreeDSProviderException("secret provider detail"));

        enroll(VALID_BODY).andExpect(status().isNotFound());
        enroll(VALID_BODY).andExpect(status().isNotFound());
        enroll(VALID_BODY).andExpect(status().isConflict());
        enroll(VALID_BODY).andExpect(status().isConflict());
        enroll(VALID_BODY).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The 3DS provider could not process the request"));
    }

    private static final String SETUP_BODY = """
            {
              "paymentId": "7f0c1f5e-0000-4000-8000-000000000001",
              "card": {"number": "4111111111111111", "expirationMonth": "12", "expirationYear": "2028"}
            }
            """;

    private org.springframework.test.web.servlet.ResultActions setup(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/3ds/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void setupPassesThePaymentIdToTheServiceAndReturnsTheDeviceDataCollectionData() throws Exception {
        when(threeDSService.setup(any())).thenReturn(new ThreeDSSetupResponse(
                "reference-id", "jwt", "https://device-data.example", PAYMENT_ID));

        setup(SETUP_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.deviceDataCollectionUrl").value("https://device-data.example"));

        ArgumentCaptor<ThreeDSSetupRequest> captor = ArgumentCaptor.forClass(ThreeDSSetupRequest.class);
        verify(threeDSService).setup(captor.capture());
        assertEquals(PAYMENT_ID, captor.getValue().paymentId());
    }

    @Test
    void setupRequiresAPaymentId() throws Exception {
        setup(SETUP_BODY.replace("\"paymentId\": \"7f0c1f5e-0000-4000-8000-000000000001\",", ""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(threeDSService);
    }

    @Test
    void setupMapsAMissingPaymentTo404AndAPaymentInTheWrongStateTo409() throws Exception {
        when(threeDSService.setup(any()))
                .thenThrow(new PaymentNotFoundException(PAYMENT_ID))
                .thenThrow(new PaymentInvalidStateException(
                        PAYMENT_ID, PaymentStatus.THREE_DS_PENDING, PaymentStatus.THREE_DS_PENDING));

        setup(SETUP_BODY).andExpect(status().isNotFound());
        setup(SETUP_BODY).andExpect(status().isConflict());
    }

    private static final String VALIDATION_BODY = """
            {
              "paymentId": "7f0c1f5e-0000-4000-8000-000000000001",
              "card": {"number": "4111111111111111", "expirationMonth": "12", "expirationYear": "2028"}
            }
            """;

    private org.springframework.test.web.servlet.ResultActions validate(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/3ds/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void validateReturnsOnlyThePaymentIdAndOutcome() throws Exception {
        when(threeDSService.validate(any())).thenReturn(
                new ThreeDSValidationResponse(PAYMENT_ID, ThreeDSValidationOutcome.AUTHENTICATED));

        validate(VALIDATION_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.outcome").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.authenticationValue").doesNotExist())
                .andExpect(jsonPath("$.eci").doesNotExist())
                .andExpect(jsonPath("$.xid").doesNotExist());
    }

    @Test
    void validatePassesThePaymentIdAndCardToTheService() throws Exception {
        when(threeDSService.validate(any())).thenReturn(
                new ThreeDSValidationResponse(PAYMENT_ID, ThreeDSValidationOutcome.FAILED));

        validate(VALIDATION_BODY).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("FAILED"));

        ArgumentCaptor<ThreeDSValidationRequest> captor = ArgumentCaptor.forClass(ThreeDSValidationRequest.class);
        verify(threeDSService).validate(captor.capture());
        assertEquals(PAYMENT_ID, captor.getValue().paymentId());
        assertEquals("4111111111111111", captor.getValue().card().number());
    }

    @Test
    void validateRejectsAnInvalidBody() throws Exception {
        validate("{}").andExpect(status().isBadRequest());
        validate("{\"paymentId\": \"7f0c1f5e-0000-4000-8000-000000000001\"}").andExpect(status().isBadRequest());
        validate(VALIDATION_BODY.replace("\"4111111111111111\"", "\"\"")).andExpect(status().isBadRequest());
        validate(VALIDATION_BODY.replace("7f0c1f5e-0000-4000-8000-000000000001", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(threeDSService);
    }

    @Test
    void validateMapsDomainErrorsToHttpStatuses() throws Exception {
        when(threeDSService.validate(any()))
                .thenThrow(new ThreeDSSessionNotFoundException(PAYMENT_ID))
                .thenThrow(new ThreeDSInvalidStateException(PAYMENT_ID, ThreeDSSessionStatus.VALIDATING, "validated"))
                .thenThrow(new OptimisticLockingFailureException("claimed by another request"))
                .thenThrow(new ThreeDSProviderException("secret provider detail"));

        validate(VALIDATION_BODY).andExpect(status().isNotFound());
        validate(VALIDATION_BODY).andExpect(status().isConflict());
        validate(VALIDATION_BODY).andExpect(status().isConflict());
        validate(VALIDATION_BODY).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The 3DS provider could not process the request"));
    }
}
