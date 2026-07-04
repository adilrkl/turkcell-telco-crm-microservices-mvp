package com.turkcell.customerservice.application.features.customer.command.delete;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.repository.CustomerRepository;

/**
 * Musteri soft-delete (FR-04, KVKK): satir silinmez, {@code deletedAt} damgalanir.
 * @SQLRestriction sayesinde bundan sonra hicbir sorgu musteriyi dondurmez.
 * Zaten silinmis musteri (@SQLRestriction ile bulunamaz) -> 404.
 */
@Component
public class DeleteCustomerCommandHandler implements CommandHandler<DeleteCustomerCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(DeleteCustomerCommandHandler.class);

    private final CustomerRepository repository;

    public DeleteCustomerCommandHandler(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @CacheEvict(value = "customerById", key = "#command.id")
    public Void handle(DeleteCustomerCommand command) {
        Customer customer = repository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", command.id().toString()));
        customer.setDeletedAt(Instant.now());
        customer.setUpdatedAt(Instant.now());
        repository.save(customer);
        log.info("customer: soft-delete uygulandi. id={}", command.id());
        return null;
    }
}
