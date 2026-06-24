package com.turkcell.commonlib.saga;

/**
 * Saga topic (Kafka destination) isimleri - producer kodu ile config YAML'lari
 * ayni isimleri kullansin diye tek kaynak.
 *
 * <ul>
 *   <li>{@link #SUBSCRIPTION_COMMANDS} / {@link #PAYMENT_COMMANDS}: orchestrator (order) -> participant komutlari.</li>
 *   <li>{@link #SAGA_REPLIES}: participant -> orchestrator reply'lari.</li>
 *   <li>{@link #ORDER_EVENTS}: orchestrator -> dis dunyaya domain event'leri (notification/billing).</li>
 * </ul>
 */
public final class SagaTopics {

    private SagaTopics() {
    }

    public static final String SUBSCRIPTION_COMMANDS = "subscription-commands";
    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String SAGA_REPLIES = "saga-replies";
    public static final String ORDER_EVENTS = "order-events";
}
