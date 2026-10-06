package com.payverse.paymentapi.config;

import com.payverse.paymentapi.threeds.api.ThreeDSController;
import com.payverse.paymentapi.threeds.application.ThreeDSService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ThreeDSController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=https://shop.example")
class CorsConfigTest {

    private static final String SETUP = "/api/v1/3ds/setup";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ThreeDSService threeDSService;

    @Test
    void allowsAPreflightFromAConfiguredOrigin() throws Exception {
        mockMvc.perform(options(SETUP)
                        .header("Origin", "https://shop.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example"));
    }

    @Test
    void rejectsAPreflightFromAnUnlistedOrigin() throws Exception {
        mockMvc.perform(options(SETUP)
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMethodsThe3dsFlowDoesNotUse() throws Exception {
        mockMvc.perform(options(SETUP)
                        .header("Origin", "https://shop.example")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }
}
