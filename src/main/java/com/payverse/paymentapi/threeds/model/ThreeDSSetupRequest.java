package com.payverse.paymentapi.threeds.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ThreeDSSetupRequest(@NotNull UUID paymentId, @NotNull @Valid ThreeDSCard card) {
}
