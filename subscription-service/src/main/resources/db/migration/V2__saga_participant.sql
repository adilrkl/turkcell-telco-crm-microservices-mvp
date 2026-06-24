-- Saga participant donusumu (Flyway immutability: V1 sabit, V2 ile genisletiyoruz).

ALTER TABLE subscriptions ADD COLUMN order_id UUID;          -- saga korelasyonu
ALTER TABLE subscriptions ADD CONSTRAINT uq_subscriptions_order_id UNIQUE (order_id);

-- Inbox (idempotent consumer): islenen komut event id'leri.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Transactional outbox: reply event'leri saga-replies topic'ine buradan publish edilir.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50),
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    destination    VARCHAR(100) NOT NULL,
    payload        TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER NOT NULL DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP,
    published_at   TIMESTAMP
);
CREATE INDEX idx_subscription_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';

-- Denetim izi (MVP dokumani §13: subscription servisinde audit zorunlu).
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    service_name  VARCHAR(50) NOT NULL,
    entity_name   VARCHAR(50) NOT NULL,
    entity_id     UUID,
    action        VARCHAR(50) NOT NULL,
    detail        TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- MSISDN havuzu seed: rezerve edilebilecek bos numaralar (status FREE).
INSERT INTO msisdn_pool (msisdn, status) VALUES
 ('905320000001','FREE'), ('905320000002','FREE'), ('905320000003','FREE'), ('905320000004','FREE'),
 ('905320000005','FREE'), ('905320000006','FREE'), ('905320000007','FREE'), ('905320000008','FREE'),
 ('905320000009','FREE'), ('905320000010','FREE'), ('905320000011','FREE'), ('905320000012','FREE'),
 ('905320000013','FREE'), ('905320000014','FREE'), ('905320000015','FREE'), ('905320000016','FREE'),
 ('905320000017','FREE'), ('905320000018','FREE'), ('905320000019','FREE'), ('905320000020','FREE')
ON CONFLICT (msisdn) DO NOTHING;
