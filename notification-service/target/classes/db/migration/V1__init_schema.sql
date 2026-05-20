CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    channel VARCHAR(10) NOT NULL,
    locale CHAR(5) NOT NULL DEFAULT 'tr-TR',
    subject VARCHAR(255),
    body_template TEXT NOT NULL
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    template_code VARCHAR(100) NOT NULL REFERENCES notification_templates(code),
    channel VARCHAR(10) NOT NULL,
    payload_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP
);
CREATE INDEX idx_notifications_status ON notifications(status) WHERE status = 'PENDING';
