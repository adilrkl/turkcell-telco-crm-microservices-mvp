package com.turkcell.customerservice.application.features.customer.query.getbyid;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.repository.CustomerRepository;

@Component
public class GetCustomerByIdQueryHandler implements QueryHandler<GetCustomerByIdQuery, CustomerResponse> {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public GetCustomerByIdQueryHandler(CustomerRepository repository, CustomerMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Cacheable(value = "customerById", key = "#query.id")
    public CustomerResponse handle(GetCustomerByIdQuery query) {
        Customer c = repository.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", query.id().toString()));
        return mapper.toResponse(c);
    }
}
