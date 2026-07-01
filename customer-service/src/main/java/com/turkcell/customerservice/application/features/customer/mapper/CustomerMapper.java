package com.turkcell.customerservice.application.features.customer.mapper;

import org.springframework.stereotype.Component;

import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;

/** Customer entity -> response donusumu. */
@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getType(), c.getFirstName(), c.getLastName(), c.getStatus());
    }
}
