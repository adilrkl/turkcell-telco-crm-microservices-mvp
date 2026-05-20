CREATE TABLE msisdn_pool (
    msisdn VARCHAR(20) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'FREE',
    reserved_until TIMESTAMP
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    msisdn VARCHAR(20) REFERENCES msisdn_pool(msisdn),
    tariff_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    activated_at TIMESTAMP,
    suspended_at TIMESTAMP,
    terminated_at TIMESTAMP
);

CREATE TABLE sim_cards (
    iccid VARCHAR(22) PRIMARY KEY,
    imsi VARCHAR(15) NOT NULL UNIQUE,
    msisdn VARCHAR(20) REFERENCES msisdn_pool(msisdn),
    status VARCHAR(20) NOT NULL DEFAULT 'STOCK'
);
