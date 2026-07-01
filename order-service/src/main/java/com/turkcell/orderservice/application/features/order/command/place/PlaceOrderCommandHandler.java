package com.turkcell.orderservice.application.features.order.command.place;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.orderservice.application.features.order.mapper.OrderMapper;
import com.turkcell.orderservice.application.features.order.rule.OrderBusinessRules;
import com.turkcell.orderservice.client.CustomerClient;
import com.turkcell.orderservice.client.ProductCatalogClient;
import com.turkcell.orderservice.client.dto.CustomerEnvelope;
import com.turkcell.orderservice.client.dto.CustomerEnvelope.CustomerView;
import com.turkcell.orderservice.client.dto.TariffEnvelope;
import com.turkcell.orderservice.client.dto.TariffEnvelope.TariffView;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.exception.InvalidOrderException;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.saga.OrderSagaOrchestrator;

import feign.FeignException;

/**
 * Siparis alma akisi:
 *  1) Senkron dogrulama (OpenFeign): musteri aktif mi + tarife/fiyat.
 *  2) Order'i PENDING_PAYMENT olarak yaz ve saga'yi baslat (ReserveMsisdn ilk komut).
 * Kafka'ya dogrudan yazilmaz; orchestrator + OutboxPoller asenkron ilerletir.
 */
@Component
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, OrderResponse> {

    private static final String CURRENCY = "TRY";

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductCatalogClient catalogClient;
    private final OrderSagaOrchestrator orchestrator;
    private final OrderMapper orderMapper;
    private final OrderBusinessRules businessRules;

    public PlaceOrderCommandHandler(OrderRepository orderRepository,
                                    CustomerClient customerClient,
                                    ProductCatalogClient catalogClient,
                                    OrderSagaOrchestrator orchestrator,
                                    OrderMapper orderMapper,
                                    OrderBusinessRules businessRules) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.catalogClient = catalogClient;
        this.orchestrator = orchestrator;
        this.orderMapper = orderMapper;
        this.businessRules = businessRules;
    }

    @Override
    @Transactional
    public OrderResponse handle(PlaceOrderCommand command) {
        // 1) Musteri dogrulama - senkron (OpenFeign)
        CustomerView customer = fetchCustomer(command.customerId());
        businessRules.customerMustBeActive(customer);

        // 2) Tarife/fiyat cekme - senkron (OpenFeign -> Redis cache'li endpoint)
        TariffView tariff = fetchTariff(command.tariffCode());
        BigDecimal price = tariff.monthlyFee();

        // 3) Siparis + kalem (PENDING_PAYMENT)
        Order order = orderMapper.toPendingOrder(command, tariff, price, CURRENCY);
        orderRepository.save(order);

        // 4) Saga'yi baslat - AYNI transaction icinde (saga_states + ilk komut outbox'a yazilir)
        orchestrator.start(order, command.customerId(), tariff.code(), price, CURRENCY);

        return orderMapper.toResponse(order, tariff.code());
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
