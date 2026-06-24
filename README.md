# Telco CRM Platform

Spring Boot 4.0.6 ve Spring Cloud 2025.1.1 tabanlı, çok modüllü Maven mikroservis projesi.
Hocanın (Halit Kalaycı / GYGY5) konu konu işlediği yapılar — **Config Server, Keycloak,
Redis cache, Kafka Transactional Outbox/Inbox, OpenFeign, BFF ve Observability** — bu telco CRM sistemine
entegre edilmiştir.

## Ekip

- Adil Arkalı
- Erol Koçoğlu
- Ayşe Ulaşlı
- Eren Eray Yılmaz

## Teknoloji Stack'i

- **Java 21**
- **Spring Boot 4.0.6** — `webmvc` starter (servlet stack)
- **Spring Cloud 2025.1.1** — Netflix Eureka, Gateway (MVC), Config, OpenFeign, Stream (Kafka binder)
- **PostgreSQL 16** — servis başına ayrı veritabanı (database-per-service)
- **Flyway** — versiyonlu DB migration (`spring-boot-starter-flyway` + `flyway-database-postgresql`)
- **Redis 7** — Spring Cache (okuma yoğun servislerde)
- **Apache Kafka 4.2 (KRaft)** — Spring Cloud Stream ile event akışı
- **Keycloak 26.1** — OAuth2/OIDC kimlik sağlayıcı (tek IdP)
- **Springdoc OpenAPI 3.0.3** — servis bazlı OpenAPI spec + Swagger UI
- **Micrometer + OpenTelemetry + Grafana LGTM** — metrics, distributed tracing ve log korelasyonu
- **Docker Compose** — yerel altyapı
- **Maven** — multi-module build

## Servisler ve Portlar

| Servis | Port | Açıklama |
|---|---|---|
| eureka-server | 8761 | Service registry |
| **config-server** | **8889** | Spring Cloud Config (native backend) |
| gateway-server | 8888 | API Gateway (Spring Cloud Gateway MVC) |
| **bff-server** | **9000** | Backend-for-Frontend (session + OAuth2 login + TokenRelay) |
| identity-service | 8081 | Profil servisi (auth Keycloak'ta) |
| customer-service | 8082 | Müşteri yönetimi (**Redis cache**) |
| product-catalog-service | 8083 | Ürün/tarife kataloğu (**Redis cache**) |
| order-service | 8084 | Sipariş + **Outbox producer** + **OpenFeign** + saga |
| subscription-service | 8085 | Abonelik yönetimi |
| usage-service | 8086 | Kullanım/CDR |
| billing-service | 8087 | Faturalama (**Kafka inbox consumer**) |
| payment-service | 8088 | Ödeme |
| notification-service | 8089 | Bildirim (**Kafka inbox consumer**) |
| ticket-service | 8090 | Destek/talep |

### Altyapı (Docker) Portları

| Bileşen | Port | Not |
|---|---|---|
| PostgreSQL (servis başına 10 adet) | 5433–5442 | `postgres-<servis>-service` |
| pgAdmin | 5151 | admin@admin.com / admin |
| Kafka (broker) | 9092 | KRaft, tek node; cluster içi `kafka:19092` |
| kafka-ui | 8080 | <http://localhost:8080> |
| Redis | 6379 | AOF persistence |
| Keycloak | 8095 | <http://localhost:8095> · admin/admin · realm `telco-crm` |
| Grafana | 3000 | Observability UI · admin/admin |
| Prometheus | 9090 | `/actuator/prometheus` scrape hedefleri |
| Tempo | 3200 | Distributed trace backend |
| Loki | 3100 | Log backend |
| OTel Collector | 4317/4318 | OTLP gRPC / HTTP giriş noktası |

## Entegre Edilen Yapılar

### 1. Config Server (native backend)
Tüm servisler 6 satırlık bir stub ile açılır; gerçek config `config-server`'ın classpath'indeki
`configs/<servis-adı>.yaml` + ortak `configs/application.yaml`'dan gelir.
```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8889"
```

### 2. Keycloak (tek IdP) + OAuth2 Resource Server
- Realm `telco-crm`, roller: `CUSTOMER, CSR, CATALOG_ADMIN, BILLING_ADMIN, ADMIN`.
- Kullanıcılar: `testuser/test12345` (CUSTOMER), `csruser/test12345` (CSR+ADMIN+CATALOG_ADMIN+BILLING_ADMIN).
- Tüm iş servisleri JWT resource-server'dır (`common-lib` içindeki `ResourceServerSecurityAutoConfiguration`
  + `KeycloakRealmRoleConverter` ile `realm_access.roles → ROLE_*`).
- Method security: `@PreAuthorize` (örn. tarife yazma `CATALOG_ADMIN`, sipariş `CUSTOMER/CSR`).

### 3. Redis Cache
- `product-catalog-service` (tarifeler) ve `customer-service` (müşteri profili) okuma yolunda `@Cacheable`.
- `RedisCacheManager` + Jackson tip bilgili serializer; sayfalı sonuçlar için `common-lib`'teki `RestPage<T>`.

### 4. Kafka — Transactional Outbox / Inbox (Spring Cloud Stream)
- **Producer (order + subscription + payment):** iş değişikliği + `outbox_events` satırı AYNI transaction'da yazılır.
  `OutboxPoller` (`@Scheduled`) PENDING satırları `StreamBridge` ile satırdaki `destination` topic'ine publish edip SENT işaretler.
- **Consumer:** `@Bean Consumer<Message<byte[]>>`; payload ham JSON, `eventType` header'ına göre dispatch.
  `processed_events` (inbox) ile idempotency — aynı `eventId` tekrar gelirse atlanır.
- Event kontratları `common-lib`'te (`com.turkcell.commonlib.saga`) — tek kaynak. Bu outbox/inbox temeli üzerine **Saga** kuruldu (bkz. §9).

### 5. OpenFeign (senkron servisler-arası çağrı)
- `order-service → customer-service` (müşteri doğrulama) ve `order-service → product-catalog-service` (fiyat).
- Eureka service-id ile load-balanced; `RequestInterceptor` Bearer token'ı downstream'e taşır.

### 6. BFF (bff-server, 9000)
- `oauth2Login` (Authorization Code) — token sunucu session'ında, tarayıcıya sadece cookie.
- `TokenRelay` filtresi ile `lb://gateway-server`'a Bearer enjekte eder; SPA-dostu CSRF + OIDC logout.

### 7. Springdoc OpenAPI + Swagger UI
- REST controller olan servislerde `/v3/api-docs` ve `/swagger-ui.html` aciktir.
- Ortak OpenAPI metadata/security config'i `common-lib` tarafindan verilir; Swagger UI'daki `Authorize`
  butonu Bearer JWT kabul eder.
- Varsayilan olarak lokal/dev kullanım icin aciktir. Prod/internal olmayan ortamlarda
  `application-prod.yaml` varsayilan olarak kapatir; internal ihtiyacta env ile tekrar acilabilir.

### 8. Observability (Metrics + Traces + Logs)
- **Metrics:** servisler `/actuator/prometheus` endpoint'i açar; Prometheus bu endpoint'leri scrape eder.
- **Traces:** Spring Boot observation verisi OpenTelemetry ile OTel Collector'a, oradan Tempo'ya akar.
- **Logs:** loki4j logback appender logları Loki'ye yollar; loglarda `traceId`/`spanId` korelasyonu bulunur.
- **Grafana:** Prometheus, Tempo ve Loki datasource'ları provision edilir; metrikten trace'e, trace'ten loga geçiş yapılabilir.
- Detaylı mimari, doğrulama komutları ve sorun giderme için: [OBSERVABILITY.md](OBSERVABILITY.md)

### 9. Saga Orchestration (order = orchestrator)
- "Yeni hat siparişi" çoklu-servis işidir: **reserve MSISDN → ödeme → aktivasyon**, her adımın bir compensation'ı var.
- `order-service` orchestrator'dır (`saga_states`); `subscription-service` ve `payment-service` participant'tır.
  Komut/reply Kafka ile akar (`subscription-commands`, `payment-commands`, `saga-replies`); her serviste transactional outbox + inbox + `audit_log`.
- Hata ya da timeout'ta otomatik compensation (`RefundPayment` + `ReleaseMsisdn`) → order `CANCELLED`.
  `billing` & `notification` saga DIŞIdır; yalnızca terminal `OrderConfirmed`/`OrderCancelled`'a reaksiyon verir.
- Detaylı akış, topic topolojisi, test adımları ve compensation knob'ları için: [SAGA.md](SAGA.md)

## Başlangıç

### 1. Altyapıyı ayağa kaldır
```bash
docker compose up -d
```
PostgreSQL'ler + pgAdmin + Kafka + kafka-ui + Redis + Keycloak (realm otomatik import)
ve Grafana/Prometheus/Tempo/Loki/OTel Collector başlar.

### 2. Tüm modülleri derle
```bash
./mvnw clean install -DskipTests
```

### 3. Servisleri çalıştır (bağımlılık sırası)
```bash
# 1) config-server (8889)   -> herkesin config kaynağı, ÖNCE bu
./mvnw -pl config-server spring-boot:run
# 2) eureka-server (8761)
./mvnw -pl eureka-server spring-boot:run
# 3) iş servisleri (örnek)
./mvnw -pl product-catalog-service spring-boot:run
./mvnw -pl customer-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl billing-service spring-boot:run
./mvnw -pl notification-service spring-boot:run
# 4) gateway-server (8888)
./mvnw -pl gateway-server spring-boot:run
# 5) bff-server (9000)
./mvnw -pl bff-server spring-boot:run
```

## Demo: "Müşteri tarife siparişi verir" (uçtan uca)

```bash
# 1) CUSTOMER token al (Keycloak)
TOKEN=$(curl -s -X POST http://localhost:8095/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telco-bff \
  -d client_secret=kVqT3nP9rL7wX2mB6dF4hJ8sZ1cY5gA \
  -d username=testuser -d password=test12345 | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# 2) Tokensız -> 401, token ile -> 201/200 (gateway üzerinden)
curl -X POST http://localhost:8888/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","tariffCode":"TARIFE_M"}'
```
Bu tek çağrı **saga'yı** başlatır:
1. **Resource-server**: token doğrulanır, rol kontrol edilir.
2. **OpenFeign**: `customer-service` (doğrula) + `product-catalog-service` (fiyat = Redis cache'li).
3. **Saga başlar**: sipariş `PENDING_PAYMENT` + `saga_states(STARTED)`; ilk komut (`ReserveMsisdn`) outbox'a yazılır.
4. **Adımlar (Kafka)**: ReserveMsisdn → MSISDN rezerve → ChargePayment → ödeme → ActivateSubscription → abonelik `ACTIVE`.
5. **Tamamlanma**: order `FULFILLED`, `OrderConfirmed` → billing (bill_cycle) + notification (welcome). Son durum: `GET /api/orders/{id}`.

### Doğrulama
- Eureka: <http://localhost:8761>
- Config: `curl http://localhost:8889/order-service/dev`
- Redis cache: `docker exec telcocrm-redis redis-cli KEYS '*'`
- Kafka: kafka-ui <http://localhost:8080> → `subscription-commands` / `payment-commands` / `saga-replies` / `order-events`
- Saga: `GET http://localhost:8084/api/orders/<id>` → birkaç sn içinde `FULFILLED`. Compensation için `_FAIL` ile biten tarife siparişi → `CANCELLED` (refund + MSISDN release). Detay: [SAGA.md](SAGA.md)
- Grafana: <http://localhost:3000> → Telco CRM dashboard / Explore
- Prometheus targets: <http://localhost:9090/targets> → `spring-services` hedefleri UP olmalı
- 403: `testuser` ile `POST /api/catalog/tariffs` → 403 (sadece `CATALOG_ADMIN`)
- BFF login: tarayıcıda <http://localhost:9000/oauth2/authorization/keycloak>
- OpenAPI:
  - customer-service: <http://localhost:8082/swagger-ui.html>
  - product-catalog-service: <http://localhost:8083/swagger-ui.html>
  - order-service: <http://localhost:8084/swagger-ui.html>

## Mimari

- **Database-per-Service** — her mikroservis kendi PostgreSQL şeması
- **Config Server** — merkezi config (native backend)
- **API Gateway + BFF** — gateway servis yönlendirme; BFF session/login katmanı
- **Service Discovery** — Netflix Eureka
- **Güvenlik** — Keycloak (OAuth2/OIDC), tüm servisler JWT resource-server
- **Event-Driven** — Transactional Outbox/Inbox + Kafka (Spring Cloud Stream)
- **Saga Orchestration** — order-service orchestrator + `saga_states`; reserve→ödeme→aktivasyon, compensation'lı ([SAGA.md](SAGA.md))
- **Senkron çağrı** — OpenFeign (+ Eureka load-balancing)
- **Cache** — Redis (okuma yoğun servisler)
- **API Contract** — Springdoc OpenAPI + Swagger UI
- **Observability** — Micrometer/OpenTelemetry + Prometheus, Grafana, Tempo, Loki; `traceId` ile metric/trace/log korelasyonu

## Proje Yapısı

```
telco-crm-platform/
├── pom.xml                       # parent pom
├── docker-compose.yml            # postgres'ler + pgadmin + kafka + redis + keycloak + observability stack
├── OBSERVABILITY.md              # metrics/traces/logs mimarisi ve doğrulama adımları
├── SAGA.md                       # saga orchestration: akış, topic topolojisi, test, compensation
├── OPENAPI.md                    # springdoc/swagger ui katmanı
├── docker/keycloak/telco-crm-realm.json
├── docker/grafana/               # datasource/dashboard provisioning
├── docker/prometheus/            # scrape config
├── docker/otel/                  # OpenTelemetry Collector config
├── docker/tempo/                 # distributed tracing backend config
├── docker/loki/                  # log backend config
├── common-lib/                   # ApiResponse, exception advice, JWT converter, RestPage, saga event kontratları, autoconfig
├── config-server/                # Spring Cloud Config (native) + configs/ ağacı
├── eureka-server/                # service registry
├── gateway-server/               # API gateway
├── bff-server/                   # BFF (oauth2 login + TokenRelay)
├── identity-service/             # profil (auth Keycloak'ta)
├── customer-service/             # müşteri (Redis)
├── product-catalog-service/      # tarife kataloğu (Redis)
├── order-service/                # sipariş + Saga orchestrator (Outbox + Feign + saga_states)
├── subscription-service/         # abonelik (saga participant: MSISDN/SIM reserve/activate/release)
├── usage-service/                # kullanım
├── billing-service/              # faturalama (OrderConfirmed → bill_cycle)
├── payment-service/              # ödeme (saga participant: mock PSP charge/refund)
├── notification-service/         # bildirim (OrderConfirmed/OrderCancelled consumer)
└── ticket-service/               # destek/talep
```

## Sonraki Adımlar

- ✅ **Saga orchestration uygulandı** — order orchestrator (`saga_states`) + reserve→ödeme→aktivasyon + compensation + timeout. Bkz. [SAGA.md](SAGA.md).
- ✅ **subscription / payment servisleri** saga akışına dahil edildi (iskeletten dolduruldu).
- rate limit yapısı entegre edilecek.
- Feign çağrılarına Resilience4j circuit breaker + fallback.
- OutboxPoller için çoklu-instance güvenliği (`SELECT ... FOR UPDATE SKIP LOCKED`).
- Saga: compensation ack'lerini bekleyen iki-fazlı iptal + Kafka DLQ/retry; aylık bill-run + `InvoiceGenerated → Payment` (recurring auto-pay).

- minio