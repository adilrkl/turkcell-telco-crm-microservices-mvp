package com.turkcell.billingservice.dto;

/** Uretilen fatura PDF'i (G6, FR-23). JSON degil binary doner; controller application/pdf yazar. */
public record InvoicePdfFile(
        String fileName,
        byte[] content) {
}
