package com.turkcell.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

/** Adres ekleme istegi; isDefault true gelirse musterinin diger adresleri default olmaktan cikar. */
public record AddAddressRequest(
        @NotBlank String line1,
        String city,
        String district,
        String postalCode,
        Boolean isDefault) {
}
