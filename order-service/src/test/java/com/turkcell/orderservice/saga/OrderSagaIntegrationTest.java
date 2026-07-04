package com.turkcell.orderservice.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.turkcell.commonlib.cqrs.Mediator;
import com.turkcell.commonlib.saga.ChargePaymentCommand;
import com.turkcell.commonlib.saga.MsisdnReserved;
import com.turkcell.commonlib.saga.OrderCancelled;
import com.turkcell.commonlib.saga.OrderConfirmed;
import com.turkcell.commonlib.saga.PaymentCompleted;
import com.turkcell.commonlib.saga.PaymentFailed;
import com.turkcell.commonlib.saga.ReserveMsisdnCommand;
import com.turkcell.commonlib.saga.SagaHeaders;
import com.turkcell.commonlib.saga.SagaTopics;
import com.turkcell.commonlib.saga.SubscriptionActivated;
import com.turkcell.commonlib.saga.SubscriptionActivationFailed;
import com.turkcell.orderservice.application.features.order.command.place.PlaceOrderCommand;
import com.turkcell.orderservice.client.CustomerClient;
import com.turkcell.orderservice.client.ProductCatalogClient;
import com.turkcell.orderservice.client.dto.CustomerEnvelope;
import com.turkcell.orderservice.client.dto.CustomerEnvelope.CustomerView;
import com.turkcell.orderservice.client.dto.TariffEnvelope;
import com.turkcell.orderservice.client.dto.TariffEnvelope.TariffView;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.repository.SagaStateRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saga orchestrator entegrasyon testi — GERCEK Postgres + GERCEK Kafka (Testcontainers 2.x).
 * Siparis gercek giris yolundan verilir (Mediator -> PlaceOrderCommandHandler; Feign client'lari
 * mock). Participant'lari (subscription/payment) TEST oynar: komut topic'lerini dinler,
 * reply'lari saga-replies'a ham JSON byte + eventType header'i ile publish eder.
 *
 * Zamanlama: her saga adimi 5 sn araliklarla calisan OutboxPoller'i bekler; bu yuzden tum
 * asenkron assert'ler 60 sn'ye kadar poll eder (Thread.sleep yok).
 */
@SpringBootTest(properties = {
        "spring.cloud.function.definition=sagaReplies",
        "spring.cloud.stream.bindings.sagaReplies-in-0.destination=saga-replies",
        "spring.cloud.stream.bindings.sagaReplies-in-0.group=order-service-group",
        "eureka.client.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderSagaIntegrationTest {

    private static final String MSISDN = "905320000001";
    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(60);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        // Cloud Stream binder'i kendi broker property'sini okur (@ServiceConnection spring.kafka'yi baglar).
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    static final ObjectMapper json = JsonMapper.builder().build();

    /** Test'in participant gozlemcisi: komut/domain topic'lerinden okunan tum kayitlar. */
    static final List<ConsumerRecord<byte[], byte[]>> consumed = new ArrayList<>();
    static KafkaConsumer<byte[], byte[]> observer;
    static KafkaProducer<byte[], byte[]> replyProducer;

    @MockitoBean
    CustomerClient customerClient;

    @MockitoBean
    ProductCatalogClient catalogClient;

    /** Resource-server filter chain'i JwtDecoder bean'i ister; testte issuer yok. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    Mediator mediator;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    SagaStateRepository sagaStateRepository;

    @Autowired
    OrderSagaOrchestrator orchestrator;

    @BeforeAll
    static void startKafkaClients() throws Exception {
        createTopics();

        observer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "saga-test-observer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
        observer.subscribe(List.of(SagaTopics.SUBSCRIPTION_COMMANDS, SagaTopics.PAYMENT_COMMANDS,
                SagaTopics.ORDER_EVENTS));

        replyProducer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
    }

    @AfterAll
    static void closeKafkaClients() {
        if (observer != null) {
            observer.close();
        }
        if (replyProducer != null) {
            replyProducer.close();
        }
    }

    private static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.<String, Object>of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            try {
                admin.createTopics(List.of(
                        new NewTopic(SagaTopics.SUBSCRIPTION_COMMANDS, 1, (short) 1),
                        new NewTopic(SagaTopics.PAYMENT_COMMANDS, 1, (short) 1),
                        new NewTopic(SagaTopics.SAGA_REPLIES, 1, (short) 1),
                        new NewTopic(SagaTopics.ORDER_EVENTS, 1, (short) 1)))
                        .all().get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
    }

    // --- testler ---

    @Test
    @DisplayName("mutlu yol: reserve -> charge -> activate reply'lari ile order FULFILLED, saga COMPLETED, OrderConfirmed yayinlanir")
    void happyPathFulfillsOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = placeOrder(customerId, "TARIFE_M", "249.90");

        ReserveMsisdnCommand reserve = json.readValue(
                awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReserveMsisdnCommand", orderId).value(),
                ReserveMsisdnCommand.class);
        assertThat(reserve.customerId()).isEqualTo(customerId);
        assertThat(reserve.tariffCode()).isEqualTo("TARIFE_M");

        reply("MsisdnReserved", new MsisdnReserved(UUID.randomUUID(), orderId, MSISDN));

        ChargePaymentCommand charge = json.readValue(
                awaitEvent(SagaTopics.PAYMENT_COMMANDS, "ChargePaymentCommand", orderId).value(),
                ChargePaymentCommand.class);
        assertThat(charge.customerId()).isEqualTo(customerId);
        assertThat(charge.amount()).isEqualByComparingTo("249.90");
        assertThat(charge.currency()).isEqualTo("TRY");

        reply("PaymentCompleted", new PaymentCompleted(UUID.randomUUID(), orderId, UUID.randomUUID()));

        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ActivateSubscriptionCommand", orderId);
        UUID subscriptionId = UUID.randomUUID();
        reply("SubscriptionActivated", new SubscriptionActivated(UUID.randomUUID(), orderId, subscriptionId, MSISDN));

        awaitOrderAndSaga(orderId, OrderStatus.FULFILLED, SagaSteps.COMPLETED);

        OrderConfirmed confirmed = json.readValue(
                awaitEvent(SagaTopics.ORDER_EVENTS, "OrderConfirmed", orderId).value(),
                OrderConfirmed.class);
        assertThat(confirmed.customerId()).isEqualTo(customerId);
        assertThat(confirmed.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(confirmed.msisdn()).isEqualTo(MSISDN);
        assertThat(confirmed.tariffCode()).isEqualTo("TARIFE_M");
        assertThat(confirmed.amount()).isEqualByComparingTo("249.90");
    }

    @Test
    @DisplayName("aktivasyon hatasi: RefundPayment + ReleaseMsisdn compensation'lari yayinlanir, order CANCELLED")
    void activationFailureTriggersRefundAndRelease() {
        UUID orderId = placeOrder(UUID.randomUUID(), "TARIFE_X_FAIL", "249.90");

        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReserveMsisdnCommand", orderId);
        reply("MsisdnReserved", new MsisdnReserved(UUID.randomUUID(), orderId, MSISDN));

        awaitEvent(SagaTopics.PAYMENT_COMMANDS, "ChargePaymentCommand", orderId);
        reply("PaymentCompleted", new PaymentCompleted(UUID.randomUUID(), orderId, UUID.randomUUID()));

        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ActivateSubscriptionCommand", orderId);
        reply("SubscriptionActivationFailed",
                new SubscriptionActivationFailed(UUID.randomUUID(), orderId, "provisioning hatasi (simulated)"));

        // Odeme tahsil edilmisti: iade + rezervasyon birakma birlikte beklenir.
        awaitEvent(SagaTopics.PAYMENT_COMMANDS, "RefundPaymentCommand", orderId);
        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", orderId);

        awaitOrderAndSaga(orderId, OrderStatus.CANCELLED, SagaSteps.CANCELLED);

        OrderCancelled cancelled = json.readValue(
                awaitEvent(SagaTopics.ORDER_EVENTS, "OrderCancelled", orderId).value(),
                OrderCancelled.class);
        assertThat(cancelled.reason()).contains("Aktivasyon basarisiz");
    }

    @Test
    @DisplayName("odeme hatasi: yalnizca ReleaseMsisdn yayinlanir (refund YOK), order CANCELLED")
    void paymentFailureTriggersReleaseOnly() {
        UUID orderId = placeOrder(UUID.randomUUID(), "TARIFE_M", "249.90");

        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReserveMsisdnCommand", orderId);
        reply("MsisdnReserved", new MsisdnReserved(UUID.randomUUID(), orderId, MSISDN));

        awaitEvent(SagaTopics.PAYMENT_COMMANDS, "ChargePaymentCommand", orderId);
        reply("PaymentFailed", new PaymentFailed(UUID.randomUUID(), orderId, "tutar limiti asti: 1500.00"));

        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", orderId);
        awaitOrderAndSaga(orderId, OrderStatus.CANCELLED, SagaSteps.CANCELLED);

        OrderCancelled cancelled = json.readValue(
                awaitEvent(SagaTopics.ORDER_EVENTS, "OrderCancelled", orderId).value(),
                OrderCancelled.class);
        assertThat(cancelled.reason()).contains("Odeme basarisiz");

        // Para hic tahsil edilmedi: refund komutu YAYINLANMAMALI.
        // (Release + OrderCancelled ayni transaction'da outbox'a yazilir ve ayni poller
        // turunda publish edilir; OrderCancelled gorulduyse refund da gelmis olurdu.)
        assertNeverReceived(SagaTopics.PAYMENT_COMMANDS, "RefundPaymentCommand", orderId);
    }

    @Test
    @DisplayName("timeout suupuru: cevap gelmeyen STARTED saga iptal edilir, OrderCancelled yayinlanir")
    void timeoutSweepCancelsStalledSaga() {
        UUID orderId = placeOrder(UUID.randomUUID(), "TARIFE_M", "249.90");

        // Saga STARTED'ta bekliyor (ilk komut publish edildi ama reply gelmeyecek).
        awaitEvent(SagaTopics.SUBSCRIPTION_COMMANDS, "ReserveMsisdnCommand", orderId);

        // 2 dk duvar saati beklemek yerine suupuru gelecek cutoff ile dogrudan cagiririz.
        orchestrator.sweepTimeouts(Instant.now().plusSeconds(1));

        awaitOrderAndSaga(orderId, OrderStatus.CANCELLED, SagaSteps.CANCELLED);

        OrderCancelled cancelled = json.readValue(
                awaitEvent(SagaTopics.ORDER_EVENTS, "OrderCancelled", orderId).value(),
                OrderCancelled.class);
        assertThat(cancelled.reason()).contains("zaman asimina");

        // STARTED adiminda hicbir sey rezerve/charge edilmedi: compensation komutu olmamali.
        assertNeverReceived(SagaTopics.SUBSCRIPTION_COMMANDS, "ReleaseMsisdnCommand", orderId);
        assertNeverReceived(SagaTopics.PAYMENT_COMMANDS, "RefundPaymentCommand", orderId);
    }

    // --- yardimcilar ---

    /** Gercek giris yolu: Feign mock'lari + Mediator uzerinden PlaceOrderCommand. */
    private UUID placeOrder(UUID customerId, String tariffCode, String monthlyFee) {
        when(customerClient.getById(customerId)).thenReturn(new CustomerEnvelope(
                new CustomerView(customerId, "INDIVIDUAL", "Demo", "Musteri", "ACTIVE")));
        when(catalogClient.getByCode(tariffCode)).thenReturn(new TariffEnvelope(
                new TariffView(tariffCode, "Demo Tarife", new BigDecimal(monthlyFee), "ACTIVE",
                        1500, 1000, 15360)));

        OrderResponse response = mediator.send(new PlaceOrderCommand(customerId, tariffCode));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(sagaStateRepository.findByOrderId(response.orderId()))
                .as("saga siparisle ayni transaction'da baslamali")
                .isPresent();
        return response.orderId();
    }

    /** Reply'i saga-replies topic'ine ham JSON byte + eventType header'i ile publish eder. */
    private void reply(String eventType, Object payload) {
        ProducerRecord<byte[], byte[]> record =
                new ProducerRecord<>(SagaTopics.SAGA_REPLIES, json.writeValueAsBytes(payload));
        record.headers().add(SagaHeaders.EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
        try {
            replyProducer.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("saga reply publish edilemedi: " + eventType, e);
        }
    }

    /**
     * Beklenen event'i (topic + eventType header + payload'da orderId) gorene kadar poll eder.
     * KafkaConsumer thread-safe olmadigi icin Awaitility yerine ayni thread'de dongu kurulur.
     */
    private ConsumerRecord<byte[], byte[]> awaitEvent(String topic, String eventType, UUID orderId) {
        long deadline = System.currentTimeMillis() + KAFKA_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<ConsumerRecord<byte[], byte[]>> match = findConsumed(topic, eventType, orderId);
            if (match.isPresent()) {
                return match.get();
            }
            observer.poll(Duration.ofMillis(500)).forEach(consumed::add);
        }
        throw new AssertionError("%ds icinde beklenen event gelmedi: topic=%s type=%s order=%s"
                .formatted(KAFKA_TIMEOUT.toSeconds(), topic, eventType, orderId));
    }

    /** Terminal event gorulduktan SONRA cagrilir: bir tur daha poll edip yoklugunu dogrular. */
    private void assertNeverReceived(String topic, String eventType, UUID orderId) {
        observer.poll(Duration.ofMillis(1000)).forEach(consumed::add);
        assertThat(findConsumed(topic, eventType, orderId))
                .as("beklenmeyen event yayinlandi: %s/%s", topic, eventType)
                .isEmpty();
    }

    private Optional<ConsumerRecord<byte[], byte[]>> findConsumed(String topic, String eventType, UUID orderId) {
        return consumed.stream()
                .filter(rec -> rec.topic().equals(topic)
                        && eventType.equals(eventTypeOf(rec))
                        && new String(rec.value(), StandardCharsets.UTF_8).contains(orderId.toString()))
                .findFirst();
    }

    private static String eventTypeOf(ConsumerRecord<byte[], byte[]> record) {
        Header header = record.headers().lastHeader(SagaHeaders.EVENT_TYPE);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Order status + saga adimi DB'de beklenen degere gelene kadar bekler (binder/poller asenkron). */
    private void awaitOrderAndSaga(UUID orderId, String orderStatus, String sagaStep) {
        await().atMost(KAFKA_TIMEOUT).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(orderStatus);
            assertThat(sagaStateRepository.findByOrderId(orderId).orElseThrow().getCurrentStep()).isEqualTo(sagaStep);
        });
    }
}
