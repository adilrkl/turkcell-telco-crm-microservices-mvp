package com.turkcell.customerservice.application.features.customer.command.create;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.application.features.customer.rule.CustomerBusinessRules;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.repository.CustomerRepository;

@Component
public class CreateCustomerCommandHandler implements CommandHandler<CreateCustomerCommand, CustomerResponse> {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final CustomerBusinessRules businessRules;

    public CreateCustomerCommandHandler(CustomerRepository repository, CustomerMapper mapper,
                                        CustomerBusinessRules businessRules) {
        this.repository = repository;
        this.mapper = mapper;
        this.businessRules = businessRules;
    }

    @Override
    @Transactional
    public CustomerResponse handle(CreateCustomerCommand command) {
        businessRules.validateIdentity(command.type(), command.identityNumber());
        Customer saved = repository.save(mapper.toCustomer(command));
        return mapper.toResponse(saved);
    }
}
