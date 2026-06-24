-- Saga participant donusumu. V1'deki payments fatura-tabanliydi; onboarding tahsilati
-- order-tabanlidir (fatura YOK). Flyway immutability: V1'i degistirmiyoruz, V2 ile genisletiyoruz.

ALTER TABLE payments ALTER COLUMN invoice_id DROP NOT NULL;   -- onboarding'de fatura yok
ALTER TABLE payments ADD COLUMN order_id    UUID;             -- saga korelasyonu
ALTER TABLE payments ADD COLUMN customer_id UUID;
ALTER TABLE payments ADD COLUMN currency    CHAR(3) NOT NULL DEFAULT 'TRY';
ALTER TABLE payments ADD COLUMN created_at  TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);

-- response JSONB -> TEXT (basit metin yanit; gecerli JSON zorunlulugu olmasin).
ALTER TABLE payment_attempts ALTER COLUMN response TYPE TEXT USING response::text;

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
CREATE INDEX idx_payment_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';

-- Denetim izi (MVP dokumani §13: payment servisinde audit zorunlu).
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
