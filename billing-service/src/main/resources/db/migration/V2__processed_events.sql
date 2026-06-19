-- Inbox (idempotent consumer) tablosu: islenen Kafka event id'leri.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
