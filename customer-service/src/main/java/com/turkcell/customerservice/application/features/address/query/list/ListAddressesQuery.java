package com.turkcell.customerservice.application.features.address.query.list;

import java.util.List;
import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.customerservice.dto.AddressResponse;

public record ListAddressesQuery(UUID customerId) implements Query<List<AddressResponse>> {
}
