package com.turkcell.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.event.OrderPlacedEvent;
import com.turkcell.orderservice.client.CustomerClient;
import com.turkcell.orderservice.client.ProductCatalogClient;
import com.turkcell.orderservice.client.dto.CustomerEnvelope;
import com.turkcell.orderservice.client.dto.CustomerEnvelope.CustomerView;
import com.turkcell.orderservice.client.dto.TariffEnvelope;
import com.turkcell.orderservice.client.dto.TariffEnvelope.TariffView;
import com.turkcell.orderservice.dto.CreateOrderRequest;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.entity.OrderItem;
import com.turkcell.orderservice.entity.OutboxEvent;
import com.turkcell.orderservice.entity.OutboxStatus;
import com.turkcell.orderservice.exception.InvalidOrderException;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.repository.OutboxRepository;

import feign.FeignException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {

    private static final String CURRENCY = "TRY";

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final CustomerClient customerClient;
    private final ProductCatalogClient catalogClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        CustomerClient customerClient,
                        ProductCatalogClient catalogClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.customerClient = customerClient;
        this.catalogClient = catalogClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Senkron dogrulama (Feign) + atomik (order + outbox) yazim.
     * Kafka'ya DOGRUDAN yazilmaz; OutboxPoller asenkron publish eder.
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        // 1) Musteri dogrulama - senkron (OpenFeign)
        CustomerView customer = fetchCustomer(request.customerId());
        if (!"ACTIVE".equalsIgnoreCase(customer.status())) {
            throw new InvalidOrderException("Musteri aktif degil (status=" + customer.status() + ")");
        }

        // 2) Tarife/fiyat cekme - senkron (OpenFeign -> Redis cache'li endpoint)
        TariffView tariff = fetchTariff(request.tariffCode());
        BigDecimal price = tariff.monthlyFee();

        // 3) Siparis + kalem
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus("PLACED");
        order.setTotalAmount(price);
        order.setCurrency(CURRENCY);
        order.setCreatedAt(Instant.now());
        OrderItem item = new OrderItem();
        item.setProductCode(tariff.code());
        item.setProductType("TARIFF");
        item.setQuantity(1);
        item.setUnitPrice(price);
        order.addItem(item);
        orderRepository.save(order);

        // 4) Outbox event - AYNI transaction icinde (transactional outbox)
        UUID eventId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(eventId, order.getId(), order.getCustomerId(),
                tariff.code(), price, CURRENCY, Instant.now());
        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(UUID.randomUUID());
        outbox.setAggregateType("Order");
        outbox.setAggregateId(order.getId());
        outbox.setEventType("orderPlaced");
        outbox.setPayload(objectMapper.writeValueAsString(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setCreatedAt(Instant.now());
        outboxRepository.save(outbox);

        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(), tariff.code(), eventId);
    }

    private CustomerView fetchCustomer(UUID id) {
        try {
            CustomerEnvelope env = customerClient.getById(id);
            if (env == null || env.data() == null) {
                throw new InvalidOrderException("Musteri bulunamadi: " + id);
            }
            return env.data();
        } catch (FeignException.NotFound e) {
            throw new InvalidOrderException("Musteri bulunamadi: " + id);
        }
    }

    private TariffView fetchTariff(String code) {
        try {
            TariffEnvelope env = catalogClient.getByCode(code);
            if (env == null || env.data() == null) {
                throw new InvalidOrderException("Tarife bulunamadi: " + code);
            }
            return env.data();
        } catch (FeignException.NotFound e) {
            throw new InvalidOrderException("Tarife bulunamadi: " + code);
        }
    }
}
