package com.turkcell.customerservice.application.features.customer.query.getbyid;

import java.util.UUID;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.customerservice.dto.CustomerResponse;

/** Tekil musteri sorgusu (Redis cache'li). */
public record GetCustomerByIdQuery(UUID id) implements Query<CustomerResponse> {
}
