-- Destek talebi bildirimi (G7, FR-33): talep acilis SMS sablonu.
INSERT INTO notification_templates (code, channel, locale, subject, body_template) VALUES
  ('TICKET_OPENED', 'SMS', 'tr-TR', 'Talebiniz Alindi',
   'Destek talebiniz olusturulmustur. En kisa surede ilgili ekibimiz donus yapacaktir.')
ON CONFLICT (code) DO NOTHING;
