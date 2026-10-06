package com.payverse.paymentapi.threeds.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ThreeDSSetupRequest(@NotNull @Valid ThreeDSCard card) {
}
