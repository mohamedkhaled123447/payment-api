package com.payverse.paymentapi.threeds.api;

import com.payverse.paymentapi.threeds.application.ThreeDSInvalidStateException;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.application.ThreeDSService;
import com.payverse.paymentapi.threeds.application.ThreeDSSessionNotFoundException;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentOutcome;
import com.payverse.paymentapi.threeds.model.ThreeDSEnrollmentResponse;
import com.payverse.paymentapi.threeds.persistence.ThreeDSSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
              "amount": 49.90,
              "currency": "USD",
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
        enroll(VALID_BODY.replace("\"USD\"", "\"US\"")).andExpect(status().isBadRequest());
        enroll(VALID_BODY.replace("49.90", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void enrollMapsDomainErrorsToHttpStatuses() throws Exception {
        when(threeDSService.enroll(any(), any()))
                .thenThrow(new ThreeDSSessionNotFoundException(PAYMENT_ID))
                .thenThrow(new ThreeDSInvalidStateException(PAYMENT_ID, ThreeDSSessionStatus.AUTHENTICATED))
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenThrow(new ThreeDSProviderException("secret provider detail"));

        enroll(VALID_BODY).andExpect(status().isNotFound());
        enroll(VALID_BODY).andExpect(status().isConflict());
        enroll(VALID_BODY).andExpect(status().isConflict());
        enroll(VALID_BODY).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The 3DS provider could not process the request"));
    }
}
