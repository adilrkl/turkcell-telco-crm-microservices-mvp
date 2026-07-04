package com.turkcell.customerservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Belge yukleme istegi (mock): gercek dosya tasinmaz, fileName'den bir fileRef uretilir.
 * type: ID_CARD | PASSPORT | DRIVER_LICENSE | OTHER
 */
public record UploadDocumentRequest(
        @NotBlank String type,
        @NotBlank String fileName) {
}
