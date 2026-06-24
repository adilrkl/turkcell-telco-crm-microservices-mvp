-- Saga orchestration + outbox routing.
-- Outbox artik birden fazla topic'e yazar (subscription-commands, payment-commands, order-events);
-- hangi topic'e gidecegini 'destination' kolonu tutar (OutboxPoller buna gore route eder).
ALTER TABLE outbox_events ADD COLUMN destination VARCHAR(100);
UPDATE outbox_events SET destination = 'order-events' WHERE destination IS NULL;

-- Inbox: order, saga reply'larini idempotent tuketsin (ayni eventId iki kez gelirse atla).
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Saga timeout taramasi icin: terminal olmayan + eski last_updated satirlarini hizli bul.
CREATE INDEX idx_saga_states_last_updated ON saga_states (last_updated);
