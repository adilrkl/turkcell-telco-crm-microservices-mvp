CREATE TABLE tariffs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    monthly_fee DECIMAL(10,2) NOT NULL,
    minutes_included INTEGER,
    sms_included INTEGER,
    data_mb_included INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE NOT NULL,
    effective_to DATE
);

CREATE TABLE addons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    validity_days INTEGER
);

CREATE TABLE tariff_addons (
    tariff_id UUID NOT NULL REFERENCES tariffs(id),
    addon_id UUID NOT NULL REFERENCES addons(id),
    PRIMARY KEY (tariff_id, addon_id)
);
