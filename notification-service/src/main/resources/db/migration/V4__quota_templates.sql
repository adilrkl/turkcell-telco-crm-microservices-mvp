-- Kota esik bildirimleri (G1, docx senaryo 14.3): %80 uyari + %100 asim SMS sablonlari.
INSERT INTO notification_templates (code, channel, locale, subject, body_template) VALUES
  ('QUOTA_WARNING_80', 'SMS', 'tr-TR', 'Kota Uyarisi',
   'Donemlik kullanim kotanizin %80''ine ulastiniz. Kalan hakkinizi Hesabim''dan izleyebilirsiniz.'),
  ('QUOTA_EXCEEDED', 'SMS', 'tr-TR', 'Kota Asimi',
   'Donemlik kullanim kotaniz doldu. Bundan sonraki kullanimlar asim ucreti olarak faturaniza yansitilacaktir.')
ON CONFLICT (code) DO NOTHING;
