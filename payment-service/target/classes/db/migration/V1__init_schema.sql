CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_ref VARCHAR(200),
    idempotency_key VARCHAR(100) UNIQUE,
    paid_at TIMESTAMP
);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_no SMALLINT NOT NULL DEFAULT 1,
    response JSONB,
    attempted_at TIMESTAMP NOT NULL DEFAULT now()
);
