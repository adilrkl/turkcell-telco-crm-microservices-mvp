-- Demo musterisi (order akisinda Feign ile dogrulanir). Sabit UUID kullanilir.
INSERT INTO customers (id, type, first_name, last_name, identity_number, status, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'INDIVIDUAL', 'Ahmet', 'Yilmaz', '12345678901', 'ACTIVE', now())
ON CONFLICT (id) DO NOTHING;
