package com.turkcell.billingservice.application.features.invoice.query.pdf;

import java.util.UUID;

import com.turkcell.billingservice.dto.InvoicePdfFile;
import com.turkcell.commonlib.cqrs.Query;

/** Fatura PDF sorgusu (G6, FR-23): PDF istek aninda uretilir, saklanmaz (MinIO Faz 6). */
public record GetInvoicePdfQuery(UUID id) implements Query<InvoicePdfFile> {
}
