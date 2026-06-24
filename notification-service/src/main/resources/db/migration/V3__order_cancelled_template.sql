-- Saga basarisizlik bildirimi icin sablon (OrderCancelled -> notifications.template_code FK'si).
INSERT INTO notification_templates (code, channel, locale, subject, body_template)
VALUES ('ORDER_CANCELLED', 'SMS', 'tr-TR', 'Siparis Iptali', 'Siparisiniz tamamlanamadi ve iptal edildi.')
ON CONFLICT (code) DO NOTHING;
