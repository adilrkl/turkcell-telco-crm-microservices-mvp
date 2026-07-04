package com.turkcell.orderservice.application.features.order.command.cancel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.cqrs.CommandHandler;
import com.turkcell.commonlib.exception.ResourceNotFoundException;
import com.turkcell.orderservice.application.features.order.mapper.OrderMapper;
import com.turkcell.orderservice.application.features.order.rule.OrderBusinessRules;
import com.turkcell.orderservice.dto.OrderResponse;
import com.turkcell.orderservice.entity.Order;
import com.turkcell.orderservice.repository.OrderRepository;
import com.turkcell.orderservice.saga.OrderSagaOrchestrator;

/**
 * Manuel siparis iptali (G5, docx §8.3): yalniz terminal-oncesi (PENDING_PAYMENT/PAID)
 * siparisler iptal edilebilir. Compensation mevcut saga altyapisindan yeniden kullanilir:
 * ulasilan adima gore ReleaseMsisdn / RefundPayment + ReleaseMsisdn tetiklenir
 * (timeout supurucusuyle ayni kurallar). Durum degisikligi + compensation komutlari +
 * OrderCancelled event'i TEK transaction'da outbox'a yazilir.
 */
@Component
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, OrderResponse> {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderCommandHandler.class);

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator orchestrator;
    private final OrderMapper orderMapper;
    private final OrderBusinessRules businessRules;

    public CancelOrderCommandHandler(OrderRepository orderRepository,
                                     OrderSagaOrchestrator orchestrator,
                                     OrderMapper orderMapper,
                                     OrderBusinessRules businessRules) {
        this.orderRepository = orderRepository;
        this.orchestrator = orchestrator;
        this.orderMapper = orderMapper;
        this.businessRules = businessRules;
    }

    @Override
    @Transactional
    public OrderResponse handle(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", command.orderId().toString()));
        businessRules.orderMustBeCancellable(order);

        orchestrator.cancelManually(order.getId(), buildReason(command.reason()));

        log.info("order={} manuel iptal edildi. actor={}", order.getId(), command.actorUserId());
        String tariffCode = order.getItems().isEmpty() ? null : order.getItems().get(0).getProductCode();
        return orderMapper.toResponse(order, tariffCode);
    }

    /** OrderCancelled.reason'a gider; notification bu metni musteri mesajinda kullanir. */
    private static String buildReason(String userReason) {
        return userReason == null || userReason.isBlank()
                ? "Siparis manuel iptal edildi"
                : "Siparis manuel iptal edildi: " + userReason;
    }
}
