package com.turkcell.customerservice.application.features.kyc.command.reject;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.exception.InvalidKycStateException;
import com.turkcell.customerservice.repository.CustomerRepository;

/** KYC reddi: PENDING -> REJECTED (siparis acilamaz; event yayinlanmaz). */
@Component
public class RejectKycCommandHandler implements CommandHandler<RejectKycCommand, CustomerResponse> {

    private static final Logger log = LoggerFactory.getLogger(RejectKycCommandHandler.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;

    public RejectKycCommandHandler(CustomerRepository customerRepository, CustomerMapper mapper) {
        this.customerRepository = customerRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    @CacheEvict(value = "customerById", key = "#command.customerId")
    public CustomerResponse handle(RejectKycCommand command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", command.customerId()));

        if (!"PENDING".equals(customer.getStatus())) {
            throw new InvalidKycStateException(
                    "KYC reddi yalniz PENDING musteride yapilabilir (mevcut: " + customer.getStatus() + ")");
        }

        customer.setStatus("REJECTED");
        customer.setUpdatedAt(Instant.now());
        Customer saved = customerRepository.save(customer);

        log.info("kyc: musteri reddedildi -> REJECTED. customer={} sebep={}", saved.getId(), command.reason());
        return mapper.toResponse(saved);
    }
}
