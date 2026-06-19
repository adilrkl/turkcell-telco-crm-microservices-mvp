-- Inbox (idempotent consumer) tablosu.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);

-- OrderPlaced bildirimi icin sablon (notifications.template_code FK'si bunu gerektirir).
INSERT INTO notification_templates (code, channel, locale, subject, body_template)
VALUES ('ORDER_CONFIRMED', 'SMS', 'tr-TR', 'Siparis Onayi', 'Siparisiniz alindi. Tesekkur ederiz!')
ON CONFLICT (code) DO NOTHING;
