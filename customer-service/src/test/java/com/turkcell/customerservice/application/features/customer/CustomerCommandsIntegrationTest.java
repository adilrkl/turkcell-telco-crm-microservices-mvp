package com.turkcell.customerservice.application.features.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.customerservice.application.features.customer.command.create.CreateCustomerCommand;
import com.turkcell.customerservice.application.features.customer.command.create.CreateCustomerCommandHandler;
import com.turkcell.customerservice.application.features.customer.command.delete.DeleteCustomerCommand;
import com.turkcell.customerservice.application.features.customer.command.delete.DeleteCustomerCommandHandler;
import com.turkcell.customerservice.application.features.customer.mapper.CustomerMapper;
import com.turkcell.customerservice.application.features.customer.query.getbyid.GetCustomerByIdQuery;
import com.turkcell.customerservice.application.features.customer.query.getbyid.GetCustomerByIdQueryHandler;
import com.turkcell.customerservice.application.features.customer.rule.CustomerBusinessRules;
import com.turkcell.customerservice.dto.CustomerResponse;
import com.turkcell.customerservice.exception.InvalidCustomerException;
import com.turkcell.customerservice.repository.CustomerRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Musteri olusturma dogrulamasi (FR-01 TCKN/VKN) + soft-delete (FR-04) entegrasyon testi —
 * GERCEK Postgres (Testcontainers). Soft-delete sonrasi @SQLRestriction ile musteri okunamaz.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CreateCustomerCommandHandler.class, DeleteCustomerCommandHandler.class,
        GetCustomerByIdQueryHandler.class, CustomerMapper.class, CustomerBusinessRules.class})
@Testcontainers(disabledWithoutDocker = true)
class CustomerCommandsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CreateCustomerCommandHandler createHandler;

    @Autowired
    DeleteCustomerCommandHandler deleteHandler;

    @Autowired
    GetCustomerByIdQueryHandler getByIdHandler;

    @Autowired
    CustomerRepository customerRepository;

    @PersistenceContext
    EntityManager em;

    @Test
    @DisplayName("gecerli TCKN ile bireysel musteri PENDING olusur")
    void createIndividualWithValidTcknSucceeds() {
        CustomerResponse response = createHandler.handle(new CreateCustomerCommand(
                "INDIVIDUAL", "Ayse", "Demir", "10000000146", null));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.identityNumber()).isEqualTo("10000000146");
        assertThat(customerRepository.findById(response.id())).isPresent();
    }

    @Test
    @DisplayName("gecersiz TCKN ile olusturma reddedilir (422 InvalidCustomerException)")
    void createWithInvalidTcknIsRejected() {
        assertThatThrownBy(() -> createHandler.handle(new CreateCustomerCommand(
                "INDIVIDUAL", "Veli", "Kaya", "12345678901", null)))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessageContaining("TCKN");
    }

    @Test
    @DisplayName("kurumsal musteri VKN ile dogrulanir; gecersiz VKN reddedilir")
    void corporateRequiresValidVkn() {
        CustomerResponse ok = createHandler.handle(new CreateCustomerCommand(
                "CORPORATE", "Acme", "Ltd", "1111111114", null));
        assertThat(ok.status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> createHandler.handle(new CreateCustomerCommand(
                "CORPORATE", "Foo", "Inc", "1111111111", null)))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessageContaining("VKN");
    }

    @Test
    @DisplayName("soft-delete sonrasi musteri hicbir sorguda gorunmez (getById 404)")
    void softDeleteHidesCustomerFromAllQueries() {
        UUID id = createHandler.handle(new CreateCustomerCommand(
                "INDIVIDUAL", "Zeynep", "Celik", "10000000146", null)).id();

        deleteHandler.handle(new DeleteCustomerCommand(id));
        // Ayni transaction'daki L1 cache'i bosalt: findById gercek SELECT atsin (prod'da
        // her istek ayri tx -> ayri persistence context oldugu icin bu adim gerekmez).
        em.flush();
        em.clear();

        // @SQLRestriction("deleted_at is null") -> silinen musteri bulunamaz
        assertThat(customerRepository.findById(id)).isEmpty();
        assertThatThrownBy(() -> getByIdHandler.handle(new GetCustomerByIdQuery(id)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("olmayan musteri silinmeye calisilinca 404")
    void deleteUnknownCustomerThrows() {
        assertThatThrownBy(() -> deleteHandler.handle(new DeleteCustomerCommand(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
