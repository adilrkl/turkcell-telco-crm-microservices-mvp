package com.turkcell.customerservice.application.features.customer.query.getall;

import java.util.List;

import com.turkcell.commonlib.cqrs.Query;
import com.turkcell.customerservice.dto.CustomerResponse;

/** Tum musterileri getiren sorgu (yalnizca CSR/ADMIN). */
public record GetAllCustomersQuery() implements Query<List<CustomerResponse>> {
}
