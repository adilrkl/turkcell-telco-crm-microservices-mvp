-- KYC mini akisi (G3 / FR-02-03): CustomerKYCApproved event'i icin transactional outbox.
-- addresses/documents tablolari V1'den beri vardir; bu migration yalniz outbox ekler
-- (billing V3'teki tabloyla ayni DDL - platform deseni).
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
CREATE INDEX idx_customer_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
