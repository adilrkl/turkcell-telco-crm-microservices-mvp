-- KYC onay bildirimi (G3, docx senaryo 14.1): hesap aktif SMS sablonu.
INSERT INTO notification_templates (code, channel, locale, subject, body_template) VALUES
  ('KYC_APPROVED', 'SMS', 'tr-TR', 'Hesabiniz Aktif',
   'Kimlik dogrulamaniz tamamlandi, hesabiniz aktif edildi. Artik tarife siparisi verebilirsiniz.')
ON CONFLICT (code) DO NOTHING;
