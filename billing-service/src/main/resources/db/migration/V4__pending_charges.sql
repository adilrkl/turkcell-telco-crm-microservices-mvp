-- Kota asim ucretleri (G1 / FR-20): usage-service'in OverageRecorded event'lerinden
-- biriktirilen bekleyen kalemler. Bir sonraki bill-run'da faturaya tip bazinda
-- kalem olarak eklenir ve BILLED'e cekilir (invoice_id set edilir).
CREATE TABLE pending_charges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    customer_id     UUID,
    type            VARCHAR(10) NOT NULL,          -- VOICE | SMS | DATA
    quantity        DECIMAL(15,4) NOT NULL,        -- asim miktari (dk / adet / MB)
    unit_price      DECIMAL(10,4) NOT NULL,        -- ingest anindaki birim fiyat (dondurulur)
    amount          DECIMAL(10,2) NOT NULL,        -- quantity * unit_price (dondurulur)
    description     VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING -> BILLED
    invoice_id      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_pending_charges_sub ON pending_charges (subscription_id) WHERE status = 'PENDING';
