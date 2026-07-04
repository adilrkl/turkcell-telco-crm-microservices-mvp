-- Demo abonelik (33333333-..., V3 usage seed'leriyle ayni) icin TARIFE_M hak fotografi
-- + icinde bulunulan ayin kotasi. Kalanlar V3'un 4 kaydi dusulmus haldedir
-- (VOICE 15.5 dk, SMS 4 adet, DATA 512 MB). Kota endpoint'i/FE kota karti demo'su icindir.
INSERT INTO subscription_entitlements
    (subscription_id, customer_id, msisdn, tariff_code, minutes_included, sms_included, mb_included)
VALUES
    ('33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
     '905550003333', 'TARIFE_M', 1500, 1000, 15360)
ON CONFLICT (subscription_id) DO NOTHING;

INSERT INTO quotas
    (subscription_id, period_start, period_end,
     minutes_total, sms_total, mb_total,
     minutes_remaining, sms_remaining, mb_remaining)
VALUES
    ('33333333-3333-3333-3333-333333333333',
     date_trunc('month', now())::date,
     (date_trunc('month', now()) + interval '1 month')::date,
     1500, 1000, 15360,
     1484.5, 996, 14848)
ON CONFLICT (subscription_id, period_start) DO NOTHING;
