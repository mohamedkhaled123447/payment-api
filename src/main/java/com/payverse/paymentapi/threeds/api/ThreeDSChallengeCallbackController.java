package com.payverse.paymentapi.threeds.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.regex.Pattern;

@Controller
@RequestMapping("/api/v1/3ds")
public class ThreeDSChallengeCallbackController {

    private static final Pattern ORIGIN = Pattern.compile("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?");

    private final String page;

    public ThreeDSChallengeCallbackController(@Value("${threeds.frontend-origin}") String frontendOrigin) {
        if (!ORIGIN.matcher(frontendOrigin).matches()) {
            throw new IllegalStateException(
                    "threeds.frontend-origin must be an origin such as https://shop.example, but was: " + frontendOrigin);
        }
        this.page = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"><title>3DS challenge complete</title></head>
                <body>
                <div>3ds-challenge-complete</div>
                <script>window.parent.postMessage({ type: "3ds-challenge-complete" }, "%s");</script>
                </body>
                </html>
                """.formatted(frontendOrigin);
    }

    @PostMapping(value = "/challenge-callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> challengeCallback() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(page);
    }
}
