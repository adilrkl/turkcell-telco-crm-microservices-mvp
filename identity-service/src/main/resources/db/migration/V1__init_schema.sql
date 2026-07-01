-- identity-service kullanici PROFIL verisini tutar.
-- Kimlik dogrulama (parola, token, oturum) tek IdP olan Keycloak'ta kalir;
-- burada yalnizca Keycloak JWT'sindeki 'sub' claim'i ile eslesen profil bilgisi durur.
CREATE TABLE user_profiles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id        VARCHAR(100) NOT NULL UNIQUE,   -- Keycloak JWT 'sub' claim'i
    username           VARCHAR(100) NOT NULL UNIQUE,
    email              VARCHAR(255) UNIQUE,            -- token'da email scope yoksa null olabilir
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    phone_number       VARCHAR(30),
    preferred_language VARCHAR(10)  NOT NULL DEFAULT 'tr',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_profiles_username ON user_profiles (username);
