package com.turkcell.customerservice.application.features.customer.command.delete;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;

/** Musteri soft-delete komutu (FR-04, KVKK). Cevap yok (Void). */
public record DeleteCustomerCommand(UUID id) implements Command<Void> {
}
