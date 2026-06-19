package com.turkcell.billingservice.service;

import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.billingservice.entity.BillCycle;
import com.turkcell.billingservice.entity.ProcessedEvent;
import com.turkcell.billingservice.repository.BillCycleRepository;
import com.turkcell.billingservice.repository.ProcessedEventRepository;
import com.turkcell.commonlib.event.OrderPlacedEvent;

/**
 * OrderPlaced isleyicisi. Inbox kontrolu + is yazimi AYNI transaction'da
 * (ayri bean oldugu icin @Transactional proxy uzerinden calisir).
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final BillCycleRepository billCycleRepository;

    public OrderEventHandler(ProcessedEventRepository processedEventRepository,
                             BillCycleRepository billCycleRepository) {
        this.processedEventRepository = processedEventRepository;
        this.billCycleRepository = billCycleRepository;
    }

    @Transactional
    public void handle(OrderPlacedEvent event) {
        // Inbox idempotency: bu event daha once islendiyse atla.
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("billing: event zaten islendi, atlaniyor. eventId={}", event.eventId());
            return;
        }

        // Is: yeni musteri siparisi icin fatura dongusu ac.
        BillCycle cycle = new BillCycle();
        cycle.setCustomerId(event.customerId());
        cycle.setDayOfMonth((short) 1);
        cycle.setNextRunDate(LocalDate.now().withDayOfMonth(1).plusMonths(1));
        billCycleRepository.save(cycle);

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setProcessedAt(Instant.now());
        processedEventRepository.save(processed);

        log.info("billing: OrderPlaced islendi -> bill_cycle acildi. order={} customer={} tutar={} {}",
                event.orderId(), event.customerId(), event.amount(), event.currency());
    }
}
