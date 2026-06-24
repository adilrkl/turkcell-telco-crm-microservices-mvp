# OpenAPI / Swagger UI - Telco CRM Platform

Bu doküman, projeye eklenen **Springdoc OpenAPI + Swagger UI** katmanını anlatır:
ne olduğu, neden eklendiği, hangi servislerde çalıştığı, nasıl kullanıldığı ve prod ortamında nasıl kapatıldığı.

> Stack: **Springdoc OpenAPI 3.0.3** - Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1 · WebMVC

---

## 1. Ne yaptık ve neden?

Mikroservis mimarisinde her servis kendi REST API sözleşmesine sahiptir.
Bu sözleşmenin hem insan tarafından okunabilir hem de makine tarafından işlenebilir olması gerekir.

Bu yüzden **Springdoc OpenAPI** eklendi:

| Katman | Ne sağlar? | Endpoint |
|---|---|---|
| OpenAPI spec | Makine okunabilir API sözleşmesi | `/v3/api-docs` |
| Swagger UI | Tarayıcıdan API keşfi ve test | `/swagger-ui.html` |
| JWT security schema | Swagger UI'da Bearer token ile deneme | `Authorize` butonu |

Buradaki doğru ayrım:

- **OpenAPI:** API sözleşme standardı.
- **Swagger UI:** OpenAPI sözleşmesini görsel ve test edilebilir arayüz olarak sunar.
- **Springdoc:** Spring Boot controller'larından OpenAPI spec üretir ve Swagger UI'ı entegre eder.

Yani projeye "eski Swagger/Springfox" değil, Spring Boot 4 ile uyumlu **Springdoc OpenAPI** eklendi.

---

## 2. Mimari

Her REST servis kendi OpenAPI dokümanını üretir. Gateway/BFF üzerinden merkezi aggregation şu an yapılmadı;
servis bazlı dokümantasyon tercih edildi.

```
Developer / Tester
        |
        |  browser
        v
  http://localhost:<service-port>/swagger-ui.html
        |
        |  loads
        v
  http://localhost:<service-port>/v3/api-docs
        |
        |  try it out + Authorization: Bearer <JWT>
        v
  protected /api/... endpoints
```

Önemli güvenlik davranışı:

- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` dokümantasyon için açıktır.
- Gerçek `/api/**` endpoint'leri JWT korumalı kalır.
- Swagger UI'da korumalı endpoint denemek için Keycloak token'ı `Authorize` butonuna girilir.

---

## 3. Bileşenler ve servisler

### Springdoc dependency yönetimi

| Dosya | Rol |
|---|---|
| `pom.xml` | `springdoc-openapi-bom` versiyon yönetimi |
| `common-lib/pom.xml` | Ortak OpenAPI auto-config için optional `springdoc-openapi-starter-common` |
| Servis POM'ları | REST servislere `springdoc-openapi-starter-webmvc-ui` |

### Swagger UI aktif olan servisler

| Servis | Port | Swagger UI | OpenAPI JSON |
|---|---:|---|---|
| customer-service | 8082 | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| product-catalog-service | 8083 | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| order-service | 8084 | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |

Şu an sadece controller'ı aktif olan servislerde eklidir. Yeni REST controller eklenen servislerde aynı dependency eklenerek devreye alınabilir.

---

## 4. Nasıl başlatılır?

### Ön koşullar

- Docker çalışıyor.
- Java 21.
- Maven wrapper mevcut: `./mvnw`.
- Config Server ve ilgili servisler ayakta.

### Adımlar

```bash
# 1) Altyapıyı kaldır
docker compose up -d

# 2) Modülleri derle
./mvnw clean install -DskipTests

# 3) Servisleri başlat
./scripts/start-all.sh
```

Tek servis çalıştırmak istersen:

```bash
./mvnw -pl customer-service spring-boot:run
./mvnw -pl product-catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
```

Sonra tarayıcıdan:

```text
http://localhost:8082/swagger-ui.html
http://localhost:8083/swagger-ui.html
http://localhost:8084/swagger-ui.html
```

---

## 5. Nasıl kullanılır / doğrulanır?

### OpenAPI spec kontrolü

```bash
curl -s http://localhost:8082/v3/api-docs | jq '.info.title'
curl -s http://localhost:8083/v3/api-docs | jq '.info.title'
curl -s http://localhost:8084/v3/api-docs | jq '.info.title'
```

Beklenen örnek çıktılar:

```text
"Customer Service API"
"Product Catalog Service API"
"Order Service API"
```

### Swagger UI'da JWT ile endpoint deneme

Önce Keycloak'tan token al:

```bash
TOKEN=$(curl -s -X POST http://localhost:8095/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telco-bff \
  -d client_secret=kVqT3nP9rL7wX2mB6dF4hJ8sZ1cY5gA \
  -d username=testuser -d password=test12345 | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

Swagger UI'da:

1. `Authorize` butonuna bas.
2. `bearer-jwt` alanına token'ı yapıştır.
3. Korumalı endpoint'i `Try it out` ile çalıştır.

Komut satırından aynı davranış:

```bash
curl -s http://localhost:8083/api/catalog/tariffs \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Prod güvenliği

Lokal/dev ortamda OpenAPI ve Swagger UI açıktır:

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_API_DOCS_ENABLED:true}
  swagger-ui:
    enabled: ${SPRINGDOC_SWAGGER_UI_ENABLED:true}
```

Prod profilinde varsayılan kapalıdır:

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_API_DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${SPRINGDOC_SWAGGER_UI_ENABLED:false}
```

Yani `prod` profilinde bilinçli env override verilmedikçe dokümantasyon endpoint'leri expose edilmez.
Internal developer portal, VPN veya private network ihtiyacı varsa env ile tekrar açılabilir:

```bash
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
```

---

## 7. Hangi dosyalar değişti / eklendi?

### Bağımlılıklar

- `pom.xml`: `springdoc-openapi.version=3.0.3` ve `springdoc-openapi-bom`.
- `common-lib/pom.xml`: optional `springdoc-openapi-starter-common`.
- `customer-service/pom.xml`: `springdoc-openapi-starter-webmvc-ui`.
- `product-catalog-service/pom.xml`: `springdoc-openapi-starter-webmvc-ui`.
- `order-service/pom.xml`: `springdoc-openapi-starter-webmvc-ui`.

### Ortak auto-config

- `common-lib/src/main/java/com/turkcell/commonlib/openapi/CommonOpenApiAutoConfiguration.java`
  - Servis ismine göre OpenAPI title üretir.
  - `bearer-jwt` security scheme ekler.
  - Swagger UI'da `Authorize` butonunu JWT için hazırlar.

- `common-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - OpenAPI auto-config'i Spring Boot'a tanıtır.

### Güvenlik

- `common-lib/src/main/java/com/turkcell/commonlib/security/ResourceServerSecurityAutoConfiguration.java`
  - Dokümantasyon yolları permit edilir:
    - `/v3/api-docs/**`
    - `/swagger-ui/**`
    - `/swagger-ui.html`
  - Gerçek API endpoint'leri JWT ister.

### Config

- `config-server/src/main/resources/configs/application.yaml`
  - Lokal/dev için Springdoc ve Swagger UI açık.
  - Swagger UI sıralama ve duration gösterimi ayarlı.

- `config-server/src/main/resources/configs/application-prod.yaml`
  - Prod profilinde OpenAPI ve Swagger UI default kapalı.

### Dokümantasyon

- `README.md`
  - Teknoloji stack'i, doğrulama linkleri ve mimari listesine OpenAPI eklendi.
- `OPENAPI.md`
  - Bu doküman.

---

## 8. Spring Boot 4 / Springdoc notları

1. **Spring Boot 4 için Springdoc 3.x kullanıldı.**
   Repo `Spring Boot 4.0.6` kullandığı için Springdoc `3.0.3` tercih edildi.

2. **WebMVC starter seçildi.**
   Proje servlet/WebMVC stack üzerinde:

   ```xml
   <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
   ```

3. **Parent dependency olarak tüm modüllere yayılmadı.**
   Sadece REST dokümantasyonu gereken servislerde eklendi.
   Böylece `config-server`, `eureka-server`, `gateway-server`, `bff-server` gibi modüller gereksiz Swagger UI dependency'si taşımaz.

4. **OpenAPI security schema ortaklaştırıldı.**
   Her serviste tek tek config yazmak yerine `common-lib` auto-config kullanıldı.

---

## 9. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| `/swagger-ui.html` 404 | Serviste `springdoc-openapi-starter-webmvc-ui` yok veya servis eski jar ile çalışıyor. `./mvnw clean install -DskipTests` + restart. |
| `/v3/api-docs` 401/403 | Ortak security config eski jar ile çalışıyor olabilir. Servisi yeniden derleyip başlat. |
| Swagger UI açılıyor ama endpoint 401 dönüyor | Normal davranış. `Authorize` ile Bearer JWT gir. |
| `Authorize` butonu görünmüyor | `CommonOpenApiAutoConfiguration` classpath'e girmemiş olabilir; servis `common-lib` dependency'sini ve auto-config imports dosyasını kontrol et. |
| Prod'da Swagger UI görünmüyor | Normal davranış. `application-prod.yaml` default kapalı yapar. Internal ortamda env ile aç. |
| Controller endpoint'i spec'te yok | Controller `@RestController` değil, yanlış package scan altında, veya servis eski jar ile çalışıyor olabilir. |

---

## 10. Kapsam ve sonraki adımlar

**Şu an kapsamda:**

- `customer-service`, `product-catalog-service`, `order-service` için Swagger UI.
- `/v3/api-docs` OpenAPI JSON üretimi.
- Ortak Bearer JWT security schema.
- Dev açık, prod default kapalı config.

**İyileştirme adayları:**

- Gateway üzerinde merkezi Swagger UI aggregation.
- OpenAPI spec export/validation adımı CI pipeline'a eklenmesi.
- Controller ve DTO'lara `@Operation`, `@Tag`, `@Schema` açıklamaları eklenmesi.
- `/api` -> `/api/v1` versiyonlama kararının netleştirilmesi ve spec'e yansıtılması.
- Hata formatı için RFC 7807 Problem Details standardının OpenAPI components altında tanımlanması.
