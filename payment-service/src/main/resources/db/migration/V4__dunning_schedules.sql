-- G8 odeme dunning retry (FR-27): basarisiz fatura tahsilatlari 24/72/168 saat araliklarla
-- yeniden denenir. Her basarisiz fatura icin bir dunning plani; scheduler due olanlari re-charge eder.
CREATE TABLE dunning_schedules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL UNIQUE,        -- bir fatura = bir dunning yasam dongusu
    customer_id      UUID NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    currency         CHAR(3) NOT NULL,
    retry_count      INTEGER NOT NULL DEFAULT 0,  -- tamamlanan retry sayisi
    max_retries      INTEGER NOT NULL,            -- = interval sayisi (varsayilan 3)
    origin_failed_at TIMESTAMP NOT NULL,          -- ilk tahsilat basarisizligi (offset'ler buna gore)
    next_retry_at    TIMESTAMP NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | RESOLVED | EXHAUSTED
    last_error       TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP
);

-- Scheduler yalniz PENDING + vadesi gelmis planlari tarar (kismi index).
CREATE INDEX idx_dunning_due ON dunning_schedules (next_retry_at) WHERE status = 'PENDING';
