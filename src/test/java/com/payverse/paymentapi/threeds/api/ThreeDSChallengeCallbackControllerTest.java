package com.payverse.paymentapi.threeds.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(ThreeDSChallengeCallbackController.class)
@TestPropertySource(properties = "threeds.frontend-origin=https://shop.example")
class ThreeDSChallengeCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsAPageThatSignalsTheCheckoutPageAtTheConfiguredOriginOnly() throws Exception {
        mockMvc.perform(post("/api/v1/3ds/challenge-callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("TransactionId", "tx-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString(
                        "window.parent.postMessage({ type: \"3ds-challenge-complete\" }, \"https://shop.example\")")))
                .andExpect(content().string(not(containsString("\"*\""))));
    }

    @Test
    void neverEchoesWhatTheBrowserSent() throws Exception {
        mockMvc.perform(post("/api/v1/3ds/challenge-callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("TransactionId", "<script>alert(1)</script>")
                        .param("MD", "evil-marker"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("alert(1)"))))
                .andExpect(content().string(not(containsString("evil-marker"))));
    }

    @Test
    void stillAnswersWhenTheBankPostsAnUnexpectedBody() throws Exception {
        mockMvc.perform(post("/api/v1/3ds/challenge-callback")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/3ds/challenge-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAcceptsPost() throws Exception {
        mockMvc.perform(get("/api/v1/3ds/challenge-callback")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void acceptsAnOriginWithAPort() {
        new ThreeDSChallengeCallbackController("http://localhost:3000");
    }

    @Test
    void refusesAnythingThatIsNotAPlainOrigin() {
        assertThrows(IllegalStateException.class, () -> new ThreeDSChallengeCallbackController("*"));
        assertThrows(IllegalStateException.class, () -> new ThreeDSChallengeCallbackController(""));
        assertThrows(IllegalStateException.class, () -> new ThreeDSChallengeCallbackController("shop.example"));
        assertThrows(IllegalStateException.class,
                () -> new ThreeDSChallengeCallbackController("https://shop.example/checkout"));
        assertThrows(IllegalStateException.class,
                () -> new ThreeDSChallengeCallbackController("https://shop.example\");alert(1);//"));
    }
}
