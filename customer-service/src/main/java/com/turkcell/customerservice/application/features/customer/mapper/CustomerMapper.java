package com.turkcell.customerservice.application.features.customer.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.turkcell.customerservice.application.features.customer.command.create.CreateCustomerCommand;
import com.turkcell.customerservice.application.features.customer.command.update.UpdateCustomerCommand;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;

/** Customer entity <-> command/response donusumleri. */
@Component
public class CustomerMapper {

    /**
     * Komuttan yeni musteri olusturur. G3 (KYC mini akisi) itibariyla yeni musteri
     * PENDING dogar; belge yukleme + KYC onayi (ADMIN) ile ACTIVE olur. Order akisi
     * ACTIVE sartini aradigi icin onay oncesi siparis acilamaz (docx senaryo 14.1).
     */
    public Customer toCustomer(CreateCustomerCommand command) {
        Customer c = new Customer();
        c.setType(command.type());
        c.setFirstName(command.firstName());
        c.setLastName(command.lastName());
        c.setIdentityNumber(command.identityNumber());
        c.setDateOfBirth(command.dateOfBirth());
        c.setStatus("PENDING");
        // created_at NOT NULL: Hibernate tum kolonlari INSERT ettigi icin DB DEFAULT'u
        // devreye girmez; uygulama tarafinda damgalanir.
        c.setCreatedAt(Instant.now());
        return c;
    }

    /** Null olmayan alanlari mevcut musteriye yansitir (partial update). */
    public void applyUpdate(Customer c, UpdateCustomerCommand command) {
        if (command.firstName() != null) c.setFirstName(command.firstName());
        if (command.lastName() != null) c.setLastName(command.lastName());
        if (command.identityNumber() != null) c.setIdentityNumber(command.identityNumber());
        if (command.dateOfBirth() != null) c.setDateOfBirth(command.dateOfBirth());
        if (command.status() != null) c.setStatus(command.status());
    }

    public CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getType(), c.getFirstName(), c.getLastName(),
                c.getIdentityNumber(), c.getDateOfBirth(), c.getStatus());
    }
}
