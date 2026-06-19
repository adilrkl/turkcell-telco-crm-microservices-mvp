package com.turkcell.customerservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.repository.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "customerById", key = "#id")
    public CustomerResponse getById(UUID id) {
        Customer c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
        return toResponse(c);
    }

    public List<CustomerResponse> findAll() {
        return repository.findAll().stream().map(CustomerService::toResponse).toList();
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getType(), c.getFirstName(), c.getLastName(), c.getStatus());
    }
}
