-- Demo profilleri (yalnizca list/getById gosterimi icin).
-- Gercek kullanicilar ilk giriste POST /api/identity/profiles/me/sync ile kendi profilini
-- olusturur; bu satirlar gercek bir Keycloak oturumuna bagli DEGILDIR (keycloak_id sabit
-- 'demo-*' degeridir, canli login'in uretecegi rastgele 'sub' ile eslesmez).
INSERT INTO user_profiles (keycloak_id, username, email, first_name, last_name, phone_number, preferred_language, status)
VALUES
  ('demo-testuser', 'testuser', 'testuser@example.com', 'Test', 'User',  '+905550000001', 'tr', 'ACTIVE'),
  ('demo-csruser',  'csruser',  'csruser@example.com',  'CSR',  'Agent', '+905550000002', 'tr', 'ACTIVE')
ON CONFLICT (keycloak_id) DO NOTHING;
