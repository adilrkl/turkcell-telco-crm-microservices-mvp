package com.turkcell.customerservice.application.features.customer.command.update;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.application.features.customer.rule.CustomerBusinessRules;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.repository.CustomerRepository;

@Component
public class UpdateCustomerCommandHandler implements CommandHandler<UpdateCustomerCommand, CustomerResponse> {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final CustomerBusinessRules businessRules;

    public UpdateCustomerCommandHandler(CustomerRepository repository, CustomerMapper mapper,
                                        CustomerBusinessRules businessRules) {
        this.repository = repository;
        this.mapper = mapper;
        this.businessRules = businessRules;
    }

    @Override
    @Transactional
    @CacheEvict(value = "customerById", key = "#command.id")
    public CustomerResponse handle(UpdateCustomerCommand command) {
        Customer customer = repository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", command.id().toString()));
        // Kimlik degistiriliyorsa mevcut tip'e gore tekrar dogrula (FR-01).
        if (command.identityNumber() != null) {
            businessRules.validateIdentity(customer.getType(), command.identityNumber());
        }
        mapper.applyUpdate(customer, command);
        return mapper.toResponse(repository.save(customer));
    }
}
