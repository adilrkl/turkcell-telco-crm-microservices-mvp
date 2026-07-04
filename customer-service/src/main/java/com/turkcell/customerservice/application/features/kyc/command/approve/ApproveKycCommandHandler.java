package com.turkcell.customerservice.application.features.kyc.command.approve;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.commonlib.saga.CustomerKYCApproved;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.exception.InvalidKycStateException;
import com.turkcell.customerservice.repository.CustomerRepository;
import com.turkcell.customerservice.repository.DocumentRepository;
import com.turkcell.customerservice.saga.OutboxWriter;

/**
 * KYC onayi (G3, docx senaryo 14.1): PENDING musteri + en az bir belge sarti ->
 * ACTIVE + belgeler verified + CustomerKYCApproved outbox'a. Hepsi TEK transaction.
 * (Order akisi ACTIVE sartini zaten aradigi icin onay oncesi siparis engellenir.)
 */
@Component
public class ApproveKycCommandHandler implements CommandHandler<ApproveKycCommand, CustomerResponse> {

    private static final Logger log = LoggerFactory.getLogger(ApproveKycCommandHandler.class);

    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;
    private final CustomerMapper mapper;
    private final OutboxWriter outbox;

    public ApproveKycCommandHandler(CustomerRepository customerRepository,
                                    DocumentRepository documentRepository,
                                    CustomerMapper mapper,
                                    OutboxWriter outbox) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.mapper = mapper;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    @CacheEvict(value = "customerById", key = "#command.customerId")
    public CustomerResponse handle(ApproveKycCommand command) {
        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", command.customerId()));

        if (!"PENDING".equals(customer.getStatus())) {
            throw new InvalidKycStateException(
                    "KYC onayi yalniz PENDING musteride yapilabilir (mevcut: " + customer.getStatus() + ")");
        }
        if (!documentRepository.existsByCustomerId(command.customerId())) {
            throw new InvalidKycStateException("KYC onayi icin en az bir belge yuklenmis olmali");
        }

        documentRepository.findByCustomerId(command.customerId()).stream()
                .filter(d -> d.getVerifiedAt() == null)
                .forEach(d -> {
                    d.setVerifiedAt(Instant.now());
                    documentRepository.save(d);
                });

        customer.setStatus("ACTIVE");
        customer.setUpdatedAt(Instant.now());
        Customer saved = customerRepository.save(customer);

        outbox.enqueue(SagaTopics.CUSTOMER_EVENTS, "CustomerKYCApproved", saved.getId(),
                new CustomerKYCApproved(UUID.randomUUID(), saved.getId(),
                        saved.getFirstName(), saved.getLastName()));

        log.info("kyc: musteri onaylandi -> ACTIVE. customer={}", saved.getId());
        return mapper.toResponse(saved);
    }
}
