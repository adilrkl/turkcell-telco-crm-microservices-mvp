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
  Sorgu `FOR UPDATE SKIP LOCKED` kullanır: poller çoklu-instance güvenlidir, her instance farklı satırları kilitleyip işler.
  Publish **sync**'tir (`kafka.default.producer.sync: true`): broker ack'i beklenir, hata poller'ın retry yoluna düşer — async'te mesaj sessizce kaybolabilirdi.
- **Consumer:** `@Bean Consumer<Message<byte[]>>`; payload ham JSON, `eventType` header'ına göre dispatch.
  `processed_events` (inbox) ile idempotency — aynı `eventId` tekrar gelirse atlanır.
  Hatada **retry + DLQ**: 3 deneme (üslü backoff 1s→10s), tükenince `error.<topic>.<group>` dead-letter topic'ine taşınır — zehirli mesaj partition'ı bloklamaz, offset commit'lenir. Tüm consumer'lara ortak (`application.yaml` → `stream.default.consumer` + `kafka.default.consumer.enable-dlq`).
- Event kontratları `common-lib`'te (`com.turkcell.commonlib.saga`) — tek kaynak. Bu outbox/inbox temeli üzerine **Saga** kuruldu (bkz. §9).

### 5. OpenFeign (senkron servisler-arası çağrı)
- `order-service → customer-service` (müşteri doğrulama) ve `order-service → product-catalog-service` (fiyat).
- Eureka service-id ile load-balanced; `RequestInterceptor` Bearer token'ı downstream'e taşır.
- Her Feign çağrısı **Resilience4j circuit breaker** ile sarılıdır (bkz. §12).

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

### 10. Rate Limiting (gateway, Redis tabanlı)
- **Neden Bucket4j?** docx §13 "Gateway'de Redis tabanlı, user başına 100 req/min" diyor ama klasik **reactive** Spring Cloud Gateway'in hazır `RedisRateLimiter` filtresini varsayıyor. Bu proje **Gateway Server WebMVC (servlet stack)** kullandığından o filtre yok; servlet-uyumlu, distributed bir çözüm olarak **Bucket4j + Redis (Lettuce, CAS)** token-bucket entegre edildi.
- **Anahtar:** kimlik doğrulanmış kullanıcı (`Authorization` Bearer JWT'sinin `sub` claim'i — gateway imza doğrulamaz, downstream resource-server'lar zaten doğrular; bu yalnızca sayaç anahtarıdır). Token yoksa istemci IP'sine (`X-Forwarded-For` ilk atlama) düşer.
- **Limit:** varsayılan **100 req/dk** (greedy refill → 100 burst + ~1.67/sn sürdürülebilir). Sayaç Redis'te (`telco:rl:<user|ip>:<id>`) tutulur; gateway yatay ölçeklenince (docx §5 stateless/HPA) tüm instance'lar aynı sayacı paylaşır.
- **Aşımda:** `429 Too Many Requests` + `Retry-After` + `X-RateLimit-Limit/Remaining/Retry-After-Seconds`; gövde proje konvansiyonuyla `ApiResponse` (`errorCode: RATE_LIMIT_EXCEEDED`). `/actuator/**` (health/Prometheus scrape) limit dışıdır.
- **Ayarlar:** config-server `gateway-server.yaml` → `telco.ratelimit.*` (`enabled`, `capacity`, `period`, `redis.*`); env ile ezilebilir (`RATELIMIT_CAPACITY` vb.).

### 11. CQRS (mediator tabanlı)
- **Framework `common-lib`'te:** `com.turkcell.commonlib.cqrs` altında `Command`/`Query`/`CommandHandler`/`QueryHandler` arayüzleri, `Mediator` + `SpringMediator` ve pipeline (`LoggingBehavior`). `CqrsAutoConfiguration` ile **auto-configuration** olarak tüm servislere dağıtılır (component-scan değil — repo konvansiyonu).
- **Feature-based:** Her servis `application/features/<entity>/{command,query,mapper,rule}` yapısını kullanır; controller yalnızca `Mediator`'a bağımlıdır (`mediator.send(command|query)`), cevabı `ApiResponse<T>` ile sarar.
- **Proxy-aware çözüm:** `@Cacheable`/`@Transactional` ile proxy'lenen handler'lar `AopProxyUtils.ultimateTargetClass` ile doğru eşleşir; cache/transaction advice korunur. İzole test: `common-lib/.../cqrs/SpringMediatorTest`.
- **Uygulanan servisler:** product-catalog (create + get/list), order (place + get), customer (get/list). Detay: [CQRS.md](CQRS.md).

### 12. Resilience4j Circuit Breaker (order-service Feign çağrıları)
- **Kapsam:** `order-service`'in iki senkron bağımlılığı — `customer-service` (müşteri doğrulama) ve `product-catalog-service` (fiyat). Downstream down/yavaş olduğunda sipariş endpoint'inin thread'leri bloke etmesi yerine hızlı ve anlamlı hata dönmesi hedeflenir (docx: "Resilience4j tüm dış çağrılarda").
- **Entegrasyon:** `spring-cloud-starter-circuitbreaker-resilience4j` + `spring.cloud.openfeign.circuitbreaker.enabled: true`; her Feign metodu kendi circuit breaker'ını alır. Varsayılan ayar `Resilience4jConfig`'te: son 10 çağrının ≥ %50'si hata → devre 10 sn AÇIK → half-open'da 3 deneme.
- **Fallback:** `FallbackFactory` — 4xx cevaplar iş hatasıdır (404 → "müşteri/tarife yok"), olduğu gibi geri fırlatılır ve devreyi SAYMAZ (status-bazlı `ignoreException` — `Retry-After` header'lı 4xx `RetryableException` olarak gelse bile); down/timeout/devre-açık ise `ServiceUnavailableException` (common-lib) → `ApiResponse` ile **503 SERVICE_UNAVAILABLE**.
- **Retry:** Feign `Retryer` — ağ-seviyesi hatada (connect/read `IOException`) 1 yeniden deneme; iki çağrı da idempotent GET. Retry circuit breaker'ın İÇİNDE çalışır, breaker yalnızca nihai sonucu sayar.
- **Thread tuzağı:** `spring.cloud.circuitbreaker.resilience4j.disable-thread-pool: true` — çağrı aynı thread'de kalır; aksi halde Bearer relay interceptor'ı (`RequestContextHolder`) ve trace context'i thread-local'dan okuyamazdı. Timeout'u Feign `connect-timeout: 2s / read-timeout: 5s` sağlar.

### 13. Recurring Billing (aylık bill-run + otomatik tahsilat)
- **Kayıt:** Saga `OrderConfirmed` yayınlarken artık `subscriptionId` da taşır; billing `bill_cycles` satırına abonelik + aylık ücret + para birimini yazar (V3).
- **Bill-run:** `BillRunService` (`@Scheduled`, demo: 1 dk; `FOR UPDATE SKIP LOCKED` → çoklu-instance güvenli) vadesi gelen döngüler için fatura keser (`ISSUED`, KDV %20, vade +15 gün) + `invoice_lines` + **aynı transaction'da** outbox'a `ChargeInvoiceCommand` yazar; döngüyü +1 ay ilerletir.
- **Auto-pay:** payment `ChargeInvoiceCommand`'i işler (mock PSP, aynı 1000 TRY limiti) → reply `InvoicePaid`/`InvoicePaymentFailed` → **`invoice-events`** topic'i → billing faturayı `PAID`/`PAYMENT_FAILED` işaretler. Her iki uçta inbox idempotency + transactional outbox.
- **Okuma API'si:** `GET /api/billing/invoices` (+`/{id}` kalemli), `GET /api/billing/bill-cycles` — sayfalı, `BILLING_ADMIN/CSR/ADMIN`.

### 14. FE Hazırlık API'leri (BFF `/api/me` + eksik okuma uçları)
- **BFF `GET /api/me`:** SPA'nın menü/route-guard ihtiyacı — session'daki access token'dan `realm_access.roles` okunur; `{username, email, fullName, roles[]}` döner.
- **BFF 401 sözleşmesi:** session düşmüş `/api/**` XHR'ına artık 302-Keycloak yerine **temiz 401** döner (`HttpStatusEntryPoint`); tarayıcı navigasyonu login redirect'inde kalır. FE axios interceptor'ı 401 → login yapar.
- **Yeni/iyileştirilen uçlar:** `GET /api/subscriptions` (+`/{id}`), `GET /api/orders` (sayfalı liste), `GET/POST/PUT /api/customers` (sayfalı + `q` araması + create/update), billing uçları (bkz. §13). Detaylı sayfa↔endpoint eşlemesi: [FRONTEND.md](FRONTEND.md) §13.
- **Keycloak:** `telco-bff` client'ına Vite dev origin'i (`http://localhost:5173`) redirect/webOrigin olarak eklendi.

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
- **CQRS** — mediator tabanlı (common-lib framework, auto-config); feature-based command/query handler'lar ([CQRS.md](CQRS.md))
- **Senkron çağrı** — OpenFeign (+ Eureka load-balancing + Resilience4j circuit breaker)
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
├── common-lib/                   # ApiResponse, exception advice, JWT converter, RestPage, saga event kontratları, CQRS mediator, autoconfig
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

Temel mimari oturdu: config, service discovery, gateway + BFF, Keycloak güvenlik, event-driven
Outbox/Inbox, Saga orchestration, mediator-tabanlı CQRS, rate limiting ve observability entegre.
Frontend de artık bu repodadır (monorepo, `frontend/`); mimari kararları için [FRONTEND.md](FRONTEND.md).

### 📍 DEVAM NOKTASI (son güncelleme: 2026-07-04, G5 manuel sipariş iptali sonrası)

**Durum:** Faz 1 ✅ · Faz 2 ✅ · Faz 3 ilerliyor · **Faz 3.5: G1 ✅ G2 ✅ G3 ✅ G4 ✅ G5 ✅** (kota zinciri +
fatura bildirimleri + KYC + abonelik yaşam döngüsü + manuel sipariş iptali; 79 test yeşil) · FE Sprint 3 merge edildi (PR #21) ·
Sıradaki backend işi: **G6 fatura PDF** (typed-client geçişi bilinçli olarak Faz 4 CI kararına ertelendi).
> ⚠️ Davranış değişikliği (G3): **yeni müşteri artık `PENDING` doğar**; sipariş verebilmesi için belge
> yükleme + `POST /api/customers/{id}/kyc/approve` (ADMIN) gerekir. Demo seed müşterisi ACTIVE kalır.

**Öncelik değişikliği (2026-07-04):** MVP analiz dokümanı (`telco-crm-microservices-mvp ... .docx`) ile satır satır
kıyas yapıldı. Sonuç: derinlikte doküman aşıldı (outbox/inbox, DLQ, test, observability), **genişlikte boşluklar var**.
Karar: önce **Faz 3.5 — docx kapsam boşlukları** (aşağıdaki G1–G9 tablosu) kapatılacak.
**Saga sertleştirme backlog'a alındı** — docx gereksinimi değil (FR-10/12 karşılandı ve `OrderSagaIntegrationTest` ile kanıtlı);
yalnızca timeout süresini config'e alma ufak işi uygun bir backend PR'ına iliştirilecek.

**Çalışma düzeni:** İki paralel track, ayrık dosya ağaçları → iki PR aynı anda açık olabilir, conflict çıkmaz.
Kural: [FRONTEND.md](FRONTEND.md) FE track'ine, bu README'nin yol haritası backend track'ine aittir.

| Sıradaki iş | Track A — Frontend (`frontend/`) | Track B — Backend |
|---|---|---|
| **1 (buradan devam)** | Sprint 4: Billing (fatura listesi + kalem detayı) ve Subscriptions sayfaları; G-işleri API çıkardıkça UI bağlanır (G1 kota kartı hazır: `GET /api/usage/quota`; G3 KYC onay/red butonları + belge listesi hazır; G4 abonelik aksiyonları hazır: `POST /api/subscriptions/{id}/suspend\|reactivate\|terminate`; G5 sipariş iptal butonu hazır: `POST /api/orders/{id}/cancel`) | **Faz 3.5:** G1 ✅ G2 ✅ G3 ✅ G4 ✅ G5 ✅ → sıradaki **G6 fatura PDF** (tablo aşağıda) |
| 2 | Typed-client (`generate:api`) geçişi — Faz 4 CI kararıyla birlikte | **Faz 4:** Dockerfile + GitHub Actions CI (test gate) — Faz 3.5 ile paralel yürüyebilir |

**Açık kararlar (bloklamıyor):** CUSTOMER self-servisi için kullanıcı↔müşteri bağlantısı (Keycloak `sub` ≠ `customerId`;
ilk FE sürümü CSR/ADMIN odaklı — [FRONTEND.md](FRONTEND.md) §13) · JaCoCo coverage eşiği (kapsam büyüyünce) ·
üretilen client'ı commit'leme vs build-time spec üretimi (Faz 4'te karara bağlanacak) · Faz 5/6 kalemleri.

**Kaldığın yerden hızlı başlatma:**
```bash
docker compose up -d                      # altyapı (keycloak realm değiştiyse: --force-recreate keycloak)
./mvnw clean install -DskipTests          # JAVA_HOME = JDK 21 olmalı
# servisleri sırayla başlat (bkz. "Başlangıç" bölümü) — 14 servis
cd frontend && npm install && npm run dev # http://localhost:5173 → "Giriş yap" → csruser/test12345
./mvnw test                               # 79 test (Kafka'lı saga IT dahil; Docker açık olmalı)
```
> Windows notu: Docker Desktop'ta Testcontainers "Docker bulunamadı" derse `~/.testcontainers.properties`
> içine `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine` yaz (Linux/CI'da gerekmez).

### 📋 Faz 3.5 — Docx kapsam tamamlama (genişlik boşlukları)

MVP analiz dokümanıyla yapılan kıyasın (2026-07-04) çıktısı. Sıra = öneri; G1–G3 kabul senaryolarını
(docx §14) kapattığı için önce onlar. Her G-işi kendi branch/PR'ı ile gider.

| # | İş | Docx ref | Kapsam |
|---|---|---|---|
| **G1 ✅** | **Kota zinciri + CDR simülatörü** — TAMAMLANDI | FR-17..20, senaryo 14.3, §10.5 | `OrderConfirmed` artık tarife haklarını taşır (event-carried state) → usage-service hak fotoğrafı + dönem kotası açar; kullanım düştükçe kota azalır (aylık lazy rollover); %80/%100 eşiğinde `QuotaThresholdReached`, kota bitince `OverageRecorded` → `quota-events` (usage outbox); notification eşik SMS'i atar (`QUOTA_WARNING_80/QUOTA_EXCEEDED`), billing aşımı `pending_charges`'a biriktirip bill-run'da KDV'li fatura kalemi yapar; kalan kota: `GET /api/usage/quota?subscriptionId=` (CSR/ADMIN); CDR simülatörü: `POST /api/usage/simulate` (dev profili + ADMIN, `usage-events`'e rastgele CDR basar) |
| **G2 ✅** | **Fatura bildirimleri** — TAMAMLANDI | senaryo 14.2, §8.8 | bill-run artık `InvoiceGenerated` domain event'i de yayınlar (billing outbox → `invoice-events`; billing'in kendi consumer'ı tipi atlar); `InvoicePaid`/`InvoicePaymentFailed` reply'ları customerId/amount/currency taşır (event-carried state, payment `ChargeInvoiceCommand`'dan bilir); notification `invoice-events`'i kendi group'uyla dinleyip üçünü de EMAIL (mock) bildirimine çevirir (`INVOICE_GENERATED/INVOICE_PAID/INVOICE_PAYMENT_FAILED` şablonları, V5); inbox-idempotency IT aynı kalıpla |
| **G3 ✅** | **KYC mini akışı** — TAMAMLANDI | FR-02/03, senaryo 14.1, §10.1 | `Address`/`Document` varlıkları (V1 tabloları artık kullanılıyor) + adres/belge endpoint'leri (`POST/GET /{id}/addresses`, `POST/GET /{id}/documents`; belge fileRef mock, MinIO Faz 6); yeni müşteri **PENDING** doğar; `POST /{id}/kyc/approve\|reject` (ADMIN) — onay şartı: PENDING + ≥1 belge → ACTIVE + belgeler `verifiedAt` damgalı + `CustomerKYCApproved` outbox'tan `customer-events`'e (customer-service'e stream-kafka + outbox/poller eklendi); notification "hesabınız aktif" SMS'i atar (`KYC_APPROVED` şablonu, V6); red → REJECTED. Order ACTIVE şartını zaten aradığı için onay öncesi sipariş engellenir |
| **G4 ✅** | **Abonelik yaşam döngüsü** — TAMAMLANDI | FR-14, §8.4 | `POST /api/subscriptions/{id}/suspend\|reactivate\|terminate` (CSR/ADMIN); durum makinesi: ACTIVE→SUSPENDED (suspend), SUSPENDED→ACTIVE (reactivate, `suspendedAt` temizlenir), ACTIVE/SUSPENDED→TERMINATED (terminal; MSISDN havuza FREE döner, aktif SIM'ler DEACTIVATED); geçersiz geçiş → 409 `SUBSCRIPTION_INVALID_STATE`; audit `actorUserId` (JWT sub) ile yazılır (§13); `SubscriptionSuspended/Reactivated/Terminated` → `subscription-events` (mevcut outbox) → notification SMS (V7 şablonları). Not: `SubscriptionReactivated` docx §8.4 publish listesinde yok, simetri için bilinçli eklendi. FE Subscriptions sayfası aksiyonları Sprint 4'te bağlanır |
| **G5 ✅** | **Manuel sipariş iptali** — TAMAMLANDI | §8.3 | `POST /api/orders/{id}/cancel` (CSR/ADMIN; gövde opsiyonel `{"reason":"..."}`) — yalnız terminal-öncesi durumlar (PENDING_PAYMENT/PAID); mevcut compensation altyapısı yeniden kullanılır: ulaşılan saga adımına göre `ReleaseMsisdn` / `RefundPayment`+`ReleaseMsisdn` (timeout süpürücüsüyle aynı kurallar, ortak yardımcıya çıkarıldı); iptal + compensation komutları + `OrderCancelled` tek transaction'da outbox'a yazılır → notification mevcut consumer'la iptal bildirimi atar; FULFILLED/CANCELLED → 409 `ORDER_INVALID`. CSR/ADMIN kısıtının gerekçesi list endpoint'iyle aynı (kullanıcı↔müşteri bağlantısı yok). Not: uçan reply'lı adım için iki-fazlı iptal (compensation ack bekleme) bilinçli olarak backlog'daki saga sertleştirmeye bırakıldı |
| **G6** | Fatura PDF | FR-23 | `GET /invoices/{id}/pdf` — basit PDF üretimi (OpenPDF vb.); MinIO/object-storage taşıması Faz 6'da kalır |
| **G7** | Ticket otomasyonu | FR-32/33 | `TicketOpened` eventi → notification; açılışta önceliğe göre `slaDueAt` otomatik hesap + basit auto-assign |
| **G8** | Ödeme dunning retry | FR-27 | başarısız fatura ödemeleri için 24/72/168 saat retry scheduler'ı (demo için sürelerin config'le kısaltılması) |
| **G9** | Küçükler | FR-01/04/30 | TCKN/VKN algoritmik doğrulama; customer soft-delete (KVKK); notification opt-in/opt-out |

**Bilinçli scope-out (yapılmayacak — docx §6.2 zaten dışarıda tutuyor ya da MVP için ağır):**
Addon/VAS kataloğu + paket değişikliği/addon siparişi (FR-05'in kalanı, FR-09'un kalanı) · tarife versiyonlama (FR-08) ·
MNP (FR-16, §6.2 scope-out) · prepaid/top-up (§6.2) · PII şifreleme + Vault (Faz 5'te) · kurumsal müşteri akışları (§6.2).

**Docx'ten bilinçli sapmalar (eksik değil, karar):** Keycloak + BFF (docx'te Keycloak "opsiyonel", baz senaryo
gateway-behind-trust + custom JWT idi; tam OIDC daha doğru ve refresh rotation'ı Keycloak hallediyor) ·
saga **orchestration** (docx §9.2 şeması choreography-vari ama §8.3 "Saga ile orchestrate" der; SAGA.md'de gerekçeli) ·
versiyonsuz `/api/**` (docx `/api/v1`) · `ApiResponse` zarfı (docx RFC 7807) · portlar 8081–8090 (docx 9001–9010) ·
el yazımı mapper (docx MapStruct) · `Idempotency-Key`/`Correlation-Id` header'ları yerine event-inbox idempotency +
OTel trace korelasyonu · FE SPA (docx'te scope-out — BFF konusunu sergilemek için bilinçli eklendi).

### ✅ Tamamlananlar
- **Abonelik yaşam döngüsü (G4, Faz 3.5)** — suspend/reactivate/terminate REST işlemleri (CSR/ADMIN, ilk yazma endpoint'leri; oluşturma yolu saga'da kalır); terminate MSISDN'i havuza döndürür + SIM'i kapatır; audit artık actor'lu (`AuditWriter`); üç domain event mevcut subscription outbox'ından `subscription-events`'e, notification SMS'e çevirir (V7). Migration gerekmedi (`suspended_at` V1'den beri vardı, entity'ye bağlandı).
- **KYC mini akışı (G3, Faz 3.5)** — yeni müşteri PENDING doğar; adres/belge API'leri (fileRef mock); ADMIN onay/red durum makinesi (onay şartı ≥1 belge); `CustomerKYCApproved` → `customer-events` (customer outbox V3) → notification SMS (V6). Not: `CustomerMapper` artık `createdAt`'i damgalar (örtük NOT NULL hatası giderildi).
- **Fatura bildirimleri (G2, Faz 3.5)** — bill-run `InvoiceGenerated` yayınlar; payment reply'ları müşteri/tutar taşır; notification `invoice-events`'ten üç fatura event'ini de EMAIL (mock) bildirimine çevirir (V5 şablonları + inbox IT).
- **Kota zinciri + CDR simülatörü (G1, Faz 3.5)** — `quotas`/`subscription_entitlements` (usage V4-V5) + usage-service outbox → `quota-events`; eşik SMS'leri (notification V4 şablonları) + `pending_charges` → bill-run aşım kalemi (billing V4). Aşım birim fiyatları billing config'te (`billing.overage.*`; ingest anında dondurulur). CDR simülatörü dev-profili endpoint'idir; kayıtlar gerçek Kafka yolundan akar.
- **Saga orchestration** — order orchestrator (`saga_states`) + reserve→ödeme→aktivasyon + compensation + timeout. Bkz. [SAGA.md](SAGA.md).
- **subscription / payment** saga akışına participant olarak dahil (outbox/inbox + `audit_log`).
- **Rate limiting** — gateway'de Bucket4j + Redis; user (JWT `sub`) / IP başına 100 req/dk; 429 + `Retry-After` + `X-RateLimit-*`. Bkz. §10.
- **CQRS (mediator)** — `common-lib` framework + auto-config; product-catalog / order / customer feature'ları. Bkz. [CQRS.md](CQRS.md).

### ✅ Faz 1 — Domain'i tamamla (boş servisler → CQRS ile) — TAMAMLANDI
İskelet halindeki üç servis CQRS vertical-slice yapısıyla dolduruldu (PR #10–#12), izlenen yol
[CQRS.md](CQRS.md) §6'daki şablondur (Flyway + entity/repo → feature handler'lar → controller yalnızca `Mediator`'a bağlı):
- ✅ **identity-service** — kullanıcı profili (auth Keycloak'ta kalır; burada profil verisi): me/get/list/update/sync feature'ları.
- ✅ **usage-service** — CDR/kullanım kaydı: Kafka'dan usage event consume (inbox idempotency) + dönem/abonelik bazlı agregasyon query'leri.
- ✅ **ticket-service** — destek talebi CRUD + durum makinesi (`OPEN→IN_PROGRESS→RESOLVED→CLOSED`) + yorum/atama; CSR rolü.

> Not: `subscription/payment` (saga participant) ve `billing/notification` (saf Kafka consumer) event-handler
> yapısındadır; bunlara controller→command/query split'i **uygulanmaz** ([CQRS.md](CQRS.md) §5).

### Faz 2 — Dayanıklılık & veri tutarlılığı
- ✅ **Resilience4j** — Feign çağrılarına (`order → customer / product-catalog`) circuit breaker + retry + timeout + fallback; 4xx devreyi saymaz, down/timeout → 503 `SERVICE_UNAVAILABLE`. Bkz. §12.
- ✅ **OutboxPoller çoklu-instance güvenliği** — `SELECT ... FOR UPDATE SKIP LOCKED` (order/subscription/payment) → yatay ölçekte çift publish yok.
- ✅ **Kafka DLQ + retry** — consumer hatalarında 3 deneme (üslü backoff) + `error.<topic>.<group>` dead-letter topic; zehirli mesaj izolasyonu (null `eventId`/bozuk payload artık sonsuz redelivery yapmaz). Tüm consumer'lara ortak (`application.yaml`).
- ✅ **Recurring billing** — aylık bill-run (`SKIP LOCKED`) + fatura kesimi + `ChargeInvoiceCommand → payment` auto-pay + `invoice-events` reply → invoice `PAID/PAYMENT_FAILED`. Bkz. §13.
- ✅ **FE hazırlık API'leri** — BFF `/api/me` + 401 sözleşmesi; subscription/billing okuma API'leri; order list; customer sayfalama/arama/create/update. Bkz. §14 ve [FRONTEND.md](FRONTEND.md).
- **Saga sertleştirme** — compensation ack'lerini bekleyen iki-fazlı iptal. *(2026-07-04: **backlog'a alındı** — docx gereksinimi değil; FR-12 compensation mevcut ve `OrderSagaIntegrationTest` ile kanıtlı. Önce Faz 3.5 docx boşlukları. Timeout süresini config'e alma ufak işi uygun bir backend PR'ına iliştirilecek.)*

### Faz 3 — Test & kalite (başladı)
- ✅ **Test altyapısı** — JaCoCo (tüm modüller, `verify`'da rapor) + **Testcontainers 2.x** BOM (Docker Engine 29 uyumu; 1.x'in docker-java'sı yeni engine API'sinde 400 alır).
- ✅ **İlk unit testler** — ticket durum makinesi (geçiş matrisi), identity uniqueness kuralları, billing bill-run matematiği (KDV/dönem/döngü ilerletme — canlı e2e değerlerinin regresyon güvencesi).
- ✅ **İlk entegrasyon testi (kalıp)** — `OrderEventHandlerIntegrationTest`: gerçek Postgres (Testcontainers `@ServiceConnection`) + Flyway; inbox idempotency (aynı `eventId` × 2 → tek `bill_cycle`). Diğer consumer'lara aynı kalıp uygulanacak.
- ✅ **Saga entegrasyon testleri** — `OrderSagaIntegrationTest` (order-service, 4 test): gerçek Postgres + gerçek Kafka (Testcontainers 2.x); sipariş gerçek giriş yolundan verilir (Mediator, Feign mock), participant'ları test oynar. Happy-path `FULFILLED`, aktivasyon hatası (`RefundPayment`+`ReleaseMsisdn`→`CANCELLED`), ödeme hatası (yalnız release), timeout süpürücü. `_FAIL` kuralı ve >1000 TRY reddi gibi participant davranışları subscription/payment slice IT'lerinde.
- ✅ **Inbox-idempotency yayılımı** — billing'deki kalıp 4 consumer'a uygulandı: subscription (4), payment (4), notification (3), usage (3); iş tabloları + reply outbox birlikte assert edilir.
- ✅ **Kota zinciri testleri (G1)** — `QuotaServiceIntegrationTest` (6): hak fotoğrafı/kota açılışı idempotency, düşüm, %80/%100 eşik (eşik başına tek event), aşım event'leri, aylık lazy rollover; notification/billing tarafında `QuotaEventHandler`/`OverageEventHandler` inbox IT'leri + bill-run aşım kalemi matematiği. Not: testlerde `logback-test.xml` (usage/notification/billing) loki4j appender'ını kapatır — aynı JVM'de ikinci Spring context'i Loki kapalıyken düşüren "Logback configuration error" bu yüzden artık oluşmaz.
- **Saga sertleştirme** — backlog (bkz. Faz 2 notu ve "DEVAM NOKTASI"); güvence ağı hazır, docx boşlukları (Faz 3.5) öne alındı.
- **Contract / API test** — OpenAPI spec doğrulama; kritik akışlar için Postman/newman koleksiyonu CI'da.
- **Coverage gate** — kapsam büyüdükçe JaCoCo eşiği (örn. %70) build'de zorunlu hale getirilecek.

### Faz 4 — Paketleme, CI/CD & deployment
- **Dockerfile** (servis başına, layered veya Jib) — şu an yalnızca altyapı compose'da; iş servislerinin imajı yok.
- **CI pipeline** (GitHub Actions) — `mvn verify` + Testcontainers + imaj build/push; PR'da otomatik gate.
- **Kubernetes / Helm** — docx §5 stateless/HPA hedefi: deployment + service + HPA + `readiness/liveness/startup` probe + ConfigMap/Secret.

### Faz 5 — Güvenlik sertleştirme (prod)
- **Secret yönetimi** — README/realm/config'teki gömülü client-secret'ları çıkar; Vault veya K8s Secret + config-server `{cipher}` şifreleme.
- **TLS/HTTPS** — gateway/BFF önünde TLS termination; servisler-arası mTLS (opsiyonel, service mesh).
- **Gateway sertleştirme** — CORS whitelist, güvenlik header'ları, downstream timeout/retry, request-size limit.
- **Keycloak prod** — dev realm import yerine yönetilen realm; token süre/rotasyon; client-credential rotasyonu.

### Faz 6 — Prod runtime & operasyon
- **Prod profilleri** — HikariCP pool tuning, actuator expose kısıtlama, `application-prod.yaml`'ı doldur (şu an yalnızca swagger'ı kapatıyor).
- **Alerting** — Prometheus Alertmanager kuralları (error-rate, saga stuck, consumer lag, 429 spike) + Grafana SLO paneli.
- **Yük testi** — k6/Gatling ile gateway + saga throughput; rate-limit davranış doğrulaması.
- **Object storage (MinIO)** — S3-uyumlu depolama: fatura PDF'i / belge saklama (usage/billing çıktıları için).
- **Runbook** — deploy/rollback, saga manuel compensation, DLQ replay prosedürleri.
