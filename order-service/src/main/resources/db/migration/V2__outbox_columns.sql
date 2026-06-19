-- Transactional Outbox icin outbox_events tablosunu genislet (hoca entity'sine uyumlu).
-- payload jsonb -> text (poller ham JSON'i bytes olarak Kafka'ya iletiyor; jsonb gerekmez).
ALTER TABLE outbox_events ALTER COLUMN payload TYPE TEXT USING payload::text;

ALTER TABLE outbox_events ADD COLUMN aggregate_type VARCHAR(50);
ALTER TABLE outbox_events ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN retry_count    INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN processed_at   TIMESTAMP;
ALTER TABLE outbox_events ADD COLUMN error_message  TEXT;

-- PENDING satirlarini hizli taramak icin kismi indeks.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
