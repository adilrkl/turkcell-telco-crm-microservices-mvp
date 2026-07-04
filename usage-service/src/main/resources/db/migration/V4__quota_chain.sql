-- Kota zinciri (G1 / FR-17..20): donemlik kota + tarife hak fotografi + outbox.
-- V1'deki quotas tablosu hic kullanilmayan bir iskeletti (entity/veri yok);
-- ALTER zinciri yerine tam sema ile yeniden kurulur.
DROP TABLE quotas;

-- Donemlik kota: kullanim dustukce *_remaining azalir; *_warned_pct, %80/%100
-- esik event'inin ayni donemde tekrar yayilmasini engeller (0 -> 80 -> 100).
CREATE TABLE quotas (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id    UUID NOT NULL,
    period_start       DATE NOT NULL,
    period_end         DATE NOT NULL,
    minutes_total      DECIMAL(15,4),
    sms_total          DECIMAL(15,4),
    mb_total           DECIMAL(15,4),
    minutes_remaining  DECIMAL(15,4),
    sms_remaining      DECIMAL(15,4),
    mb_remaining       DECIMAL(15,4),
    minutes_warned_pct SMALLINT NOT NULL DEFAULT 0,
    sms_warned_pct     SMALLINT NOT NULL DEFAULT 0,
    mb_warned_pct      SMALLINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_quotas_sub_period UNIQUE (subscription_id, period_start)
);

-- OrderConfirmed'den yazilan tarife hak fotografi: yeni donemin kotasi katalog'a
-- senkron cagri yapmadan buradan acilir (lazy rollover). Esik SMS'i icin
-- customer_id/msisdn da burada tasinir.
CREATE TABLE subscription_entitlements (
    subscription_id  UUID PRIMARY KEY,
    customer_id      UUID NOT NULL,
    msisdn           VARCHAR(20),
    tariff_code      VARCHAR(50) NOT NULL,
    minutes_included INTEGER,
    sms_included     INTEGER,
    mb_included      INTEGER,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- Transactional outbox: QuotaThresholdReached / OverageRecorded quota-events
-- topic'ine buradan publish edilir (billing V3'teki tabloyla ayni DDL - platform deseni).
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
CREATE INDEX idx_usage_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
