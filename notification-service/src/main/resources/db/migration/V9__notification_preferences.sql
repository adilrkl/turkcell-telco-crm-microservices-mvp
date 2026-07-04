-- G9 iletisim tercihleri (FR-30): kullanici kanal bazinda opt-in/opt-out. Satir YOKSA
-- varsayilan opt-in (izinli); enabled=false ise o kanaldan bildirim gonderilmez (SKIPPED).
CREATE TABLE notification_preferences (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    channel    VARCHAR(20) NOT NULL,   -- SMS | EMAIL | PUSH
    enabled    BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_pref_user_channel UNIQUE (user_id, channel)
);
CREATE INDEX idx_notification_pref_user ON notification_preferences (user_id);
