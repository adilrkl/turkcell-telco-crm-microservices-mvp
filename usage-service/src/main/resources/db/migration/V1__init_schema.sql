CREATE TABLE quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    minutes_remaining INTEGER,
    sms_remaining INTEGER,
    mb_remaining INTEGER
);

CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL,
    quantity DECIMAL(15,4) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT now(),
    cdr_ref VARCHAR(100)
);
CREATE INDEX idx_usage_subscription ON usage_records(subscription_id, recorded_at);
