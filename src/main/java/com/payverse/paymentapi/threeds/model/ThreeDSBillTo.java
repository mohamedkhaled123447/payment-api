package com.payverse.paymentapi.threeds.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreeDSBillTo(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank String address1,
        @NotBlank String locality,
        String administrativeArea,
        String postalCode,
        @NotBlank @Size(min = 2, max = 2) String country) {
}
