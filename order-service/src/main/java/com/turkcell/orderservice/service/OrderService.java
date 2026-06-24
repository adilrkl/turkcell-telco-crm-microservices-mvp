package com.turkcell.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.turkcell.orderservice.exception.InvalidOrderException;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.saga.OrderSagaOrchestrator;
import com.turkcell.orderservice.saga.OrderStatus;

import feign.FeignException;

/**
 * Siparis alma akisi:
 *  1) Senkron dogrulama (OpenFeign): musteri aktif mi + tarife/fiyat.
 *  2) Order'i PENDING_PAYMENT olarak yaz ve saga'yi baslat (ReserveMsisdn ilk komut).
 * Kafka'ya dogrudan yazilmaz; orchestrator + OutboxPoller asenkron ilerletir.
 */
@Service
public class OrderService {

    private static final String CURRENCY = "TRY";

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductCatalogClient catalogClient;
    private final OrderSagaOrchestrator orchestrator;

    public OrderService(OrderRepository orderRepository,
                        CustomerClient customerClient,
                        ProductCatalogClient catalogClient,
                        OrderSagaOrchestrator orchestrator) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.catalogClient = catalogClient;
        this.orchestrator = orchestrator;
    }

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

        // 3) Siparis + kalem (PENDING_PAYMENT)
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
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

        // 4) Saga'yi baslat - AYNI transaction icinde (saga_states + ilk komut outbox'a yazilir)
        orchestrator.start(order, request.customerId(), tariff.code(), price, CURRENCY);

        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(), tariff.code());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new InvalidOrderException("Siparis bulunamadi: " + id));
        String tariffCode = order.getItems().isEmpty() ? null : order.getItems().get(0).getProductCode();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(), tariffCode);
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
