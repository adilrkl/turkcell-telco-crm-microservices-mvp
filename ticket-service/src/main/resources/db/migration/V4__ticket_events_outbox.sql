-- G7 ticket otomasyonu (FR-32/33): team routing kolonu + TicketOpened yayini icin transactional outbox.

-- Auto-assign hedefi: talep kategoriye gore bir ekibe yonlendirilir (bireysel CSR atamasi
-- mevcut assigned_to ile elle yapilmaya devam eder). Eski satirlar icin NULL serbest.
ALTER TABLE tickets ADD COLUMN team VARCHAR(40);

-- Transactional outbox: TicketOpened event'i ticket-events topic'ine buradan publish edilir.
-- (Servis bagimsizligi korunur: outbox her serviste kendi kopyasi - bilincli karar.)
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
CREATE INDEX idx_ticket_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
