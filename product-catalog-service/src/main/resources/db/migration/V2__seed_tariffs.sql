-- Demo tarifeleri (order akisinda fiyat cekmek + Redis cache gostermek icin).
INSERT INTO tariffs (code, name, type, monthly_fee, minutes_included, sms_included, data_mb_included, status, effective_from)
VALUES
 ('TARIFE_S', 'Telco Mini',     'POSTPAID', 149.90,    500,   500,   5120, 'ACTIVE', DATE '2025-01-01'),
 ('TARIFE_M', 'Telco Standart', 'POSTPAID', 249.90,   1500,  1000,  15360, 'ACTIVE', DATE '2025-01-01'),
 ('TARIFE_L', 'Telco Sinirsiz', 'POSTPAID', 399.90, 999999, 999999, 51200, 'ACTIVE', DATE '2025-01-01')
ON CONFLICT (code) DO NOTHING;
