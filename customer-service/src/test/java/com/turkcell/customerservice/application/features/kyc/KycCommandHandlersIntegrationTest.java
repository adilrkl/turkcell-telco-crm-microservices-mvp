package com.turkcell.customerservice.application.features.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.commonlib.saga.CustomerKYCApproved;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.application.features.kyc.command.approve.ApproveKycCommand;
import com.turkcell.customerservice.application.features.kyc.command.approve.ApproveKycCommandHandler;
import com.turkcell.customerservice.application.features.kyc.command.reject.RejectKycCommand;
import com.turkcell.customerservice.application.features.kyc.command.reject.RejectKycCommandHandler;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.entity.Customer;
import com.turkcell.customerservice.entity.Document;
import com.turkcell.customerservice.entity.OutboxEvent;
import com.turkcell.customerservice.exception.InvalidKycStateException;
import com.turkcell.customerservice.repository.CustomerRepository;
import com.turkcell.customerservice.repository.DocumentRepository;
import com.turkcell.customerservice.repository.OutboxRepository;
import com.turkcell.customerservice.saga.OutboxWriter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * KYC mini akisi entegrasyon testi (G3) — GERCEK Postgres (Testcontainers) + Flyway V1..V3:
 * onay sarti (PENDING + en az bir belge), belge dogrulama damgasi, CustomerKYCApproved
 * outbox kaydi ve red gecisi. V2 demo musterisi (ACTIVE) testlerden etkilenmez;
 * her test kendi musterisini olusturur.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApproveKycCommandHandler.class, RejectKycCommandHandler.class,
        CustomerMapper.class, OutboxWriter.class})
@Testcontainers(disabledWithoutDocker = true)
class KycCommandHandlersIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }

    static final ObjectMapper json = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ApproveKycCommandHandler approveHandler;

    @Autowired
    RejectKycCommandHandler rejectHandler;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Test
    @DisplayName("onay: PENDING + belge -> ACTIVE, belge verified, CustomerKYCApproved outbox'ta")
    void approveActivatesVerifiesDocsAndEnqueuesEvent() {
        Customer customer = pendingCustomer("Ayse", "Demir");
        Document document = documentFor(customer.getId());

        CustomerResponse response = approveHandler.handle(new ApproveKycCommand(customer.getId()));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(documentRepository.findById(document.getId()).orElseThrow().getVerifiedAt()).isNotNull();

        List<OutboxEvent> events = outboxOf(customer.getId());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventType()).isEqualTo("CustomerKYCApproved");
        CustomerKYCApproved payload = json.readValue(events.getFirst().getPayload(), CustomerKYCApproved.class);
        assertThat(payload.customerId()).isEqualTo(customer.getId());
        assertThat(payload.firstName()).isEqualTo("Ayse");
    }

    @Test
    @DisplayName("onay: belge yoksa 409 ve durum PENDING kalir, event yazilmaz")
    void approveWithoutDocumentIsRejected() {
        Customer customer = pendingCustomer("Mehmet", "Kaya");

        assertThatThrownBy(() -> approveHandler.handle(new ApproveKycCommand(customer.getId())))
                .isInstanceOf(InvalidKycStateException.class)
                .hasMessageContaining("belge");

        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(outboxOf(customer.getId())).isEmpty();
    }

    @Test
    @DisplayName("onay: PENDING olmayan musteri 409 (idempotent degil, durum makinesi)")
    void approveNonPendingIsRejected() {
        Customer customer = pendingCustomer("Zeynep", "Celik");
        documentFor(customer.getId());
        approveHandler.handle(new ApproveKycCommand(customer.getId()));

        assertThatThrownBy(() -> approveHandler.handle(new ApproveKycCommand(customer.getId())))
                .isInstanceOf(InvalidKycStateException.class)
                .hasMessageContaining("PENDING");

        assertThat(outboxOf(customer.getId()))
                .as("ikinci onay yeni event uretmemeli")
                .hasSize(1);
    }

    @Test
    @DisplayName("red: PENDING -> REJECTED, event yazilmaz")
    void rejectMovesToRejected() {
        Customer customer = pendingCustomer("Ali", "Sahin");

        CustomerResponse response = rejectHandler.handle(new RejectKycCommand(customer.getId(), "belge okunaksiz"));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo("REJECTED");
        assertThat(outboxOf(customer.getId())).isEmpty();
    }

    // --- yardimcilar ---

    private Customer pendingCustomer(String firstName, String lastName) {
        Customer c = new Customer();
        c.setType("INDIVIDUAL");
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setStatus("PENDING");
        c.setCreatedAt(java.time.Instant.now());
        return customerRepository.save(c);
    }

    private Document documentFor(UUID customerId) {
        Document d = new Document();
        d.setCustomerId(customerId);
        d.setType("ID_CARD");
        d.setFileRef("mock://documents/" + customerId + "/kimlik.pdf");
        return documentRepository.save(d);
    }

    private List<OutboxEvent> outboxOf(UUID customerId) {
        return outboxRepository.findAll().stream()
                .filter(e -> customerId.equals(e.getAggregateId()))
                .toList();
    }
}
