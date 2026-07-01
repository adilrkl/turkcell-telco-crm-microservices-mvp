package com.turkcell.customerservice.application.features.customer.query.getall;

import java.util.List;

import org.springframework.stereotype.Component;

import com.turkcell.commonlib.cqrs.QueryHandler;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.repository.CustomerRepository;

@Component
public class GetAllCustomersQueryHandler implements QueryHandler<GetAllCustomersQuery, List<CustomerResponse>> {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    public GetAllCustomersQueryHandler(CustomerRepository repository, CustomerMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<CustomerResponse> handle(GetAllCustomersQuery query) {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
