package com.turkcell.commonlib.saga;

/**
 * Saga topic (Kafka destination) isimleri - producer kodu ile config YAML'lari
 * ayni isimleri kullansin diye tek kaynak.
 *
 * <ul>
 *   <li>{@link #SUBSCRIPTION_COMMANDS} / {@link #PAYMENT_COMMANDS}: orchestrator (order) -> participant komutlari.</li>
 *   <li>{@link #SAGA_REPLIES}: participant -> orchestrator reply'lari.</li>
 *   <li>{@link #ORDER_EVENTS}: orchestrator -> dis dunyaya domain event'leri (notification/billing).</li>
 *   <li>{@link #INVOICE_EVENTS}: payment -> billing fatura tahsilat reply'lari (recurring billing).</li>
 *   <li>{@link #QUOTA_EVENTS}: usage -> notification/billing kota esik + asim event'leri (kota zinciri).</li>
 *   <li>{@link #CUSTOMER_EVENTS}: customer -> dis dunyaya musteri yasam dongusu event'leri (KYC).</li>
 *   <li>{@link #SUBSCRIPTION_EVENTS}: subscription -> dis dunyaya abonelik yasam dongusu event'leri
 *       (suspend/reactivate/terminate).</li>
 *   <li>{@link #TICKET_EVENTS}: ticket -> dis dunyaya destek talebi event'leri (TicketOpened).</li>
 * </ul>
 */
public final class SagaTopics {

    private SagaTopics() {
    }

    public static final String SUBSCRIPTION_COMMANDS = "subscription-commands";
    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String SAGA_REPLIES = "saga-replies";
    public static final String ORDER_EVENTS = "order-events";
    public static final String INVOICE_EVENTS = "invoice-events";
    public static final String QUOTA_EVENTS = "quota-events";
    public static final String CUSTOMER_EVENTS = "customer-events";
    public static final String SUBSCRIPTION_EVENTS = "subscription-events";
    public static final String TICKET_EVENTS = "ticket-events";
}
