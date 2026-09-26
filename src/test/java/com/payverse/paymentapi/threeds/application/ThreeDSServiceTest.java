package com.payverse.paymentapi.threeds.application;

import com.payverse.paymentapi.threeds.model.ThreeDSCard;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupRequest;
import com.payverse.paymentapi.threeds.model.ThreeDSSetupResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThreeDSServiceTest {

    @Test
    void setupReturnsTheProviderResponse() {
        ThreeDSProvider provider = mock(ThreeDSProvider.class);
        ThreeDSService service = new ThreeDSService(provider);
        ThreeDSSetupRequest request = new ThreeDSSetupRequest(new ThreeDSCard("4111111111111111", "12", "2028"));
        ThreeDSSetupResponse expected = new ThreeDSSetupResponse(
                "reference-id",
                "access-token",
                "https://device-data.example");
        when(provider.setup(request)).thenReturn(expected);

        ThreeDSSetupResponse actual = service.setup(request);

        assertEquals(expected, actual);
        verify(provider).setup(request);
    }
}
