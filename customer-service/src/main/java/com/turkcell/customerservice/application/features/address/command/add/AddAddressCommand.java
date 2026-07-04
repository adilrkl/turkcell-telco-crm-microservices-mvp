package com.turkcell.customerservice.application.features.address.command.add;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Command;
import com.turkcell.customerservice.dto.AddressResponse;

public record AddAddressCommand(
        UUID customerId,
        String line1,
        String city,
        String district,
        String postalCode,
        boolean isDefault) implements Command<AddressResponse> {
}
