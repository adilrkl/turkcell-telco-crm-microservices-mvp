# Mimari — Telco CRM Platform

Platform mimarisi, entegre edilen yapıların tasarım kararları, Saga orchestration ve CQRS deseni.

## Mimari özet

- **Database-per-Service** — her mikroservis kendi PostgreSQL şeması
- **Config Server** — merkezi config (native backend)
- **API Gateway + BFF** — gateway servis yönlendirme; BFF session/login katmanı
- **Service Discovery** — Netflix Eureka
- **Güvenlik** — Keycloak (OAuth2/OIDC), tüm servisler JWT resource-server
- **Event-Driven** — Transactional Outbox/Inbox + Kafka (Spring Cloud Stream)
- **Saga Orchestration** — order-service orchestrator + `saga_states`; reserve→ödeme→aktivasyon, compensation'lı (aşağıdaki **Saga Orchestration** bölümü)
- **CQRS** — mediator tabanlı (common-lib framework, auto-config); feature-based command/query handler'lar (aşağıdaki **CQRS** bölümü)
- **Senkron çağrı** — OpenFeign (+ Eureka load-balancing + Resilience4j circuit breaker)
- **Cache** — Redis (okuma yoğun servisler)
- **API Contract** — Springdoc OpenAPI + Swagger UI
- **Observability** — Micrometer/OpenTelemetry + Prometheus, Grafana, Tempo, Loki; `traceId` ile metric/trace/log korelasyonu

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
- Detaylı mimari, doğrulama komutları ve sorun giderme için: [operations.md](operations.md)

### 9. Saga Orchestration (order = orchestrator)
- "Yeni hat siparişi" çoklu-servis işidir: **reserve MSISDN → ödeme → aktivasyon**, her adımın bir compensation'ı var.
- `order-service` orchestrator'dır (`saga_states`); `subscription-service` ve `payment-service` participant'tır.
  Komut/reply Kafka ile akar (`subscription-commands`, `payment-commands`, `saga-replies`); her serviste transactional outbox + inbox + `audit_log`.
- Hata ya da timeout'ta otomatik compensation (`RefundPayment` + `ReleaseMsisdn`) → order `CANCELLED`.
  `billing` & `notification` saga DIŞIdır; yalnızca terminal `OrderConfirmed`/`OrderCancelled`'a reaksiyon verir.
- Detaylı akış, topic topolojisi, test adımları ve compensation knob'ları için: aşağıdaki **Saga Orchestration** bölümü

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
- **Uygulanan servisler:** product-catalog (create + get/list), order (place + get), customer (get/list). Detay: aşağıdaki **CQRS** bölümü.

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
- **Yeni/iyileştirilen uçlar:** `GET /api/subscriptions` (+`/{id}`), `GET /api/orders` (sayfalı liste), `GET/POST/PUT /api/customers` (sayfalı + `q` araması + create/update), billing uçları (bkz. §13). Detaylı sayfa↔endpoint eşlemesi: [frontend/README.md](../frontend/README.md) §13.
- **Keycloak:** `telco-bff` client'ına Vite dev origin'i (`http://localhost:5173`) redirect/webOrigin olarak eklendi.

---

## Saga Orchestration - Telco CRM Platform

Bu doküman, projeye eklenen **Saga orchestration** katmanını anlatır:
ne olduğu, neden bu tasarımın seçildiği, hangi servislerin katıldığı, mesajlaşmanın nasıl kurulduğu,
nasıl başlatılıp test edildiği ve hata/compensation yollarının nasıl tetiklendiği.

> Stack: **Saga (orchestration) + Transactional Outbox/Inbox** - Java 21 · Spring Boot 4.0.6 · Spring Cloud Stream (Kafka) 2025.1.1 · PostgreSQL · Flyway

---

### 1. Ne yaptık ve neden?

"Yeni hat siparişi" (onboarding) tek bir servise sığmayan, **birden fazla servisin DB'sini** değiştiren bir iş akışıdır:
ödeme alınır, MSISDN (numara) ayrılır, abonelik aktive edilir. Dağıtık bir transaction'ı tek bir `@Transactional`
ile yönetemeyiz (servisler ayrı DB - database-per-service). Bu yüzden **Saga pattern** kullanıldı:

| Kavram | Ne sağlar? |
|---|---|
| Saga | Dağıtık transaction'ı, her adımın bir **geri-al (compensation)** adımı olduğu bir state machine ile yönetir |
| Orchestration | Merkezî bir koordinatör (order-service) adımları sırayla tetikler ve durumu `saga_states`'te tutar |
| Transactional Outbox | DB değişikliği + mesaj yayını **atomik** olur (aynı commit) |
| Inbox (idempotent consumer) | Aynı mesaj iki kez gelirse iş iki kez yapılmaz (`processed_events`) |

#### Tasarım kararı: dokümana sadık + ER reserve-first

MVP analiz dokümanı (§9.2) **kanonik saga akışını** tanımlar. Kararımız buna hizalandı:

- **Billing saga'da DEĞİL.** Postpaid faturalama onboarding'de fatura kesmez (önce kullan, ay sonu bill-run ile faturalan).
  Payment, onboarding'de **order tutarını** çeker (fatura değil). billing yalnızca event'e reaksiyonla `bill_cycle` açar.
- **Payment, saga adımı.** order → payment → subscription sıralaması doküman §9.2 ile aynı.
- **reserve-first (bilinçli iyileştirme).** ER'deki `MSISDN_POOL.status (FREE/RESERVED/ALLOCATED)` + `reserved_until`
  rezervasyon için tasarlanmış. Önce numarayı rezerve ederiz, ödeme gelince ALLOCATED yaparız. Böylece ödeme
  başarısız olursa **para hiç hareket etmez** (refund yerine sadece release) - en temiz compensation.

---

### 2. Mimari

order-service **orchestrator**'dır: `saga_states` tablosunda her siparişin hangi adımda olduğunu tutar,
reply event'lerini dinler ve sıradaki komutu/domain event'i yayınlar. subscription ve payment **participant**'tır:
komut alır, işini yapar, reply üretir, gerektiğinde compensation uygular.

```
POST /api/orders  ──►  order (PENDING_PAYMENT) + saga_states(STARTED)
        │
        │  (1) ReserveMsisdnCommand ─────────────►  subscription
        │                                             MSISDN_POOL: FREE → RESERVED (reserved_until)
        │                                             Subscription(PENDING)
        │  ◄──────────────────────── MsisdnReserved ─┘
        │
        │  (2) ChargePaymentCommand ─────────────►  payment
        │                                             Payment(PAID), PaymentAttempt
        │  ◄──────────────────────── PaymentCompleted ┘   → order PAID
        │
        │  (3) ActivateSubscriptionCommand ──────►  subscription
        │                                             MSISDN_POOL: RESERVED → ALLOCATED, SIM ata
        │                                             Subscription(ACTIVE)
        │  ◄──────────────────── SubscriptionActivated ┘  → order FULFILLED
        │
        └─►  OrderConfirmed  ──►  notification (welcome SMS) + billing (bill_cycle aç)

✗ HATA YOLU (compensation):
   PaymentFailed            → ReleaseMsisdn                  → order CANCELLED → OrderCancelled
   SubscriptionActivationFailed → RefundPayment + ReleaseMsisdn → order CANCELLED → OrderCancelled
   (cevap hiç gelmezse)     → SagaTimeoutScheduler 2 dk sonra iptal + compensation
```

Önemli davranışlar:

- **Senkron ön-doğrulama** (saga başlamadan): order, OpenFeign ile customer-service (aktif mi) ve
  product-catalog-service (fiyat) çağırır. Bunlar compensation'sız read'lerdir, saga adımı değildir.
- **Asenkron saga adımları**: order ↔ participant arası tüm iletişim Kafka komut/reply iledir (gevşek bağ).
- **billing & notification saga DIŞI**: yalnızca terminal `OrderConfirmed`/`OrderCancelled` domain event'lerini dinler.

---

### 3. Topic topolojisi

Tek topic (`order-events`) + tek event yerine, komut ve reply kanalları ayrıldı:

| Topic | Yön | Üretici | Tüketici |
|---|---|---|---|
| `subscription-commands` | komut | order | subscription |
| `payment-commands` | komut | order | payment |
| `saga-replies` | reply | subscription, payment | order |
| `order-events` | domain event | order | notification, billing |

Her topic birden fazla mesaj tipi taşır (örn. `saga-replies` üzerinde `PaymentCompleted`, `MsisdnReserved`...).
Tüketici, mesajın **`eventType` header**'ına bakarak doğru tipe deserialize edip dispatch eder (bkz. §6).

---

### 4. Event kontratları (`common-lib`)

Tüm komut/reply/domain event'ler **tek kaynak** olarak `common-lib`'te durur (`com.turkcell.commonlib.saga`):

| Tip | Yön | Açıklama |
|---|---|---|
| `ReserveMsisdnCommand` | order→sub | Numara rezerve et, PENDING abonelik oluştur |
| `ChargePaymentCommand` | order→pay | Order tutarını tahsil et |
| `ActivateSubscriptionCommand` | order→sub | RESERVED→ALLOCATED, SIM, ACTIVE |
| `ReleaseMsisdnCommand` | order→sub | **Compensation**: rezervasyonu/aboneliği geri al |
| `RefundPaymentCommand` | order→pay | **Compensation**: ödemeyi iade et |
| `MsisdnReserved` / `MsisdnReservationFailed` | sub→order | Rezervasyon sonucu |
| `PaymentCompleted` / `PaymentFailed` | pay→order | Tahsilat sonucu |
| `SubscriptionActivated` / `SubscriptionActivationFailed` | sub→order | Aktivasyon sonucu |
| `MsisdnReleased` / `PaymentRefunded` | sub/pay→order | Compensation ack (log) |
| `OrderConfirmed` / `OrderCancelled` | order→dış | Terminal domain event (notification/billing) |

Yardımcılar: `SagaTopics` (topic isimleri), `SagaHeaders` (`EVENT_TYPE` header anahtarı).
Her event `eventId` (idempotency) + `orderId` (saga korelasyonu) taşır.

---

### 5. Bileşenler ve servisler

| Servis | Port | Saga rolü | Eklenen ana bileşenler |
|---|---:|---|---|
| **order-service** | 8084 | Orchestrator | `OrderSagaOrchestrator`, `SagaState`(+repo), `SagaReplyConsumer`, `SagaTimeoutScheduler`, `OutboxWriter`, genelleştirilmiş `OutboxPoller`, `ProcessedEvent` inbox |
| **subscription-service** | 8085 | Participant | `SubscriptionSagaService` (reserve/activate/release), `Subscription`/`MsisdnPool`/`SimCard`, outbox+inbox+audit, `SubscriptionCommandConsumer` |
| **payment-service** | 8088 | Participant | `PaymentSagaService` (charge/refund, mock PSP), `Payment`/`PaymentAttempt`, outbox+inbox+audit, `PaymentCommandConsumer` |
| **billing-service** | 8087 | Reaktif (saga dışı) | `OrderConfirmed` → `bill_cycle` aç |
| **notification-service** | 8089 | Reaktif (saga dışı) | `OrderConfirmed` → welcome, `OrderCancelled` → iptal bildirimi |
| customer / product-catalog | 8082 / 8083 | Senkron doğrulayıcı | Değişiklik yok (Feign read) |

Her participant ve orchestrator'da aynı altyapı tekrar eder: kendi `outbox_events` + `OutboxPoller`,
kendi `processed_events` (inbox), payment/subscription'da ayrıca `audit_log` (MVP §13).

---

### 6. Mesajlaşma deseni: outbox + raw bytes + eventType dispatch

**Producer (her serviste transactional outbox):**
İş değişikliği ile `outbox_events` satırı **aynı transaction'da** yazılır. `OutboxPoller` (@Scheduled, 5 sn)
PENDING satırları okur ve `StreamBridge` ile satırdaki `destination` topic'ine **dinamik** publish eder;
mesaja `eventType` header'ı ekler, başarıyla giderse SENT işaretler.

**Consumer (functional Spring Cloud Stream):**
Her tüketici `Consumer<Message<byte[]>>` bean'idir. Payload ham JSON byte; `eventType` header'ına göre
ilgili record'a deserialize edilir (`ObjectMapper`) ve doğru handler'a dispatch edilir.

**Idempotency (inbox):** Handler işe başlamadan `processed_events`'te `eventId` var mı bakar; varsa atlar,
yoksa işi yapıp `eventId`'yi kaydeder. Aynı transaction içinde.

Neden bu desen? Tek topic'te çok mesaj tipini type-routing zahmeti olmadan taşır, mevcut outbox stiliyle
(ham JSON byte) uyumludur ve yeni mesaj tipi eklemek için yeni binding gerektirmez.

---

### 7. Order durum makinesi

`orders.status` (MVP §FR-11) ve `saga_states.current_step`:

| order.status | saga_states.current_step | Tetikleyen |
|---|---|---|
| PENDING_PAYMENT | STARTED | Sipariş alındı, ReserveMsisdn yayınlandı |
| PENDING_PAYMENT | MSISDN_RESERVED | MsisdnReserved geldi, ChargePayment yayınlandı |
| PAID | PAYMENT_COMPLETED | PaymentCompleted geldi, ActivateSubscription yayınlandı |
| FULFILLED | COMPLETED | SubscriptionActivated geldi, OrderConfirmed yayınlandı |
| CANCELLED | CANCELLED | Herhangi bir failure / timeout → compensation |

---

### 8. Nasıl başlatılır?

#### Ön koşullar
- Docker çalışıyor, Java 21, `./mvnw`.

#### Adımlar
```bash
# 1) Altyapı (postgres'ler + kafka + redis + keycloak + observability)
docker compose up -d

# 2) Tüm modülleri derle (jar'lar güncel olmalı)
./mvnw clean install -DskipTests

# 3) Servisleri bağımlılık sırasında başlat (config -> eureka -> iş servisleri -> edge)
./scripts/start-all.sh
```
Saga için kritik servisler: `order-service` (8084), `subscription-service` (8085),
`payment-service` (8088), `billing-service` (8087), `notification-service` (8089).
Durdurmak: `./scripts/stop-all.sh`.

---

### 9. Nasıl test edilir / doğrulanır?

#### 9.1 Mutlu yol (başarılı sipariş)
```bash
# CUSTOMER token al
TOKEN=$(curl -s -X POST http://localhost:8095/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telco-bff \
  -d client_secret=kVqT3nP9rL7wX2mB6dF4hJ8sZ1cY5gA \
  -d username=testuser -d password=test12345 | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# Sipariş ver (gateway üzerinden) -> 201, status PENDING_PAYMENT döner
ORDER=$(curl -s -X POST http://localhost:8888/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","tariffCode":"TARIFE_M"}')
echo "$ORDER"
OID=$(echo "$ORDER" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')

# Birkaç saniye sonra saga ilerler -> FULFILLED
sleep 5
curl -s http://localhost:8084/api/orders/$OID -H "Authorization: Bearer $TOKEN"
```
Beklenen son durum: `"status":"FULFILLED"`.

#### 9.2 Doğrulama noktaları
- **Kafka** (kafka-ui http://localhost:8080): `subscription-commands`, `payment-commands`, `saga-replies`, `order-events` topic'lerinde mesajlar.
- **order DB** (5436): `saga_states.current_step = COMPLETED`, `orders.status = FULFILLED`.
- **subscription DB** (5437): `subscriptions.status = ACTIVE`, `msisdn_pool` ilgili numara `ALLOCATED`, yeni `sim_cards` satırı, `audit_log` kayıtları.
- **payment DB** (5440): `payments.status = PAID`, `payment_attempts` APPROVED.
- **billing DB** (5439): yeni `bill_cycles` satırı.
- **notification DB** (5441): `ORDER_CONFIRMED` notification.
- **Trace**: Grafana (http://localhost:3000) → sipariş trace'i Kafka hop'larıyla birlikte.

---

### 10. Demo failure knob'ları (compensation testi)

Compensation yolunu uçtan uca görmek için iki deterministik tetikleyici:

| Senaryo | Nasıl tetiklenir | Sonuç |
|---|---|---|
| **Ödeme reddi** | Aylık ücreti **> 1000 TRY** olan bir tarife sipariş et | `PaymentFailed` → `ReleaseMsisdn` (MSISDN FREE'ye döner, abonelik CANCELLED) → order **CANCELLED** |
| **Aktivasyon hatası** | Tarife kodu **`_FAIL`** ile biten bir sipariş ver | `SubscriptionActivationFailed` → `RefundPayment` + `ReleaseMsisdn` → order **CANCELLED** |
| **Timeout** | Bir participant'ı kapat ve sipariş ver | 2 dk sonra `SagaTimeoutScheduler` iptal + ulaşılan adıma göre compensation |

Eşik `PaymentSagaService.FAIL_THRESHOLD`, `_FAIL` kuralı `SubscriptionSagaService.activate` içinde.

---

### 11. Hangi dosyalar değişti / eklendi?

#### common-lib
- `com/turkcell/commonlib/saga/` - 15 event record + `SagaTopics` + `SagaHeaders` (yeni).
- `com/turkcell/commonlib/event/OrderPlacedEvent.java` - **kaldırıldı** (yerine OrderConfirmed/OrderCancelled).

#### order-service
- `saga/OrderSagaOrchestrator.java`, `saga/SagaReplyConsumer.java`, `saga/SagaTimeoutScheduler.java`,
  `saga/OutboxWriter.java`, `saga/SagaContext.java`, `saga/SagaSteps.java`, `saga/OrderStatus.java` (yeni).
- `entity/SagaState.java`, `entity/ProcessedEvent.java`, `repository/SagaStateRepository.java`,
  `repository/ProcessedEventRepository.java` (yeni).
- `entity/OutboxEvent.java` (+`destination`), `polling/OutboxPoller.java` (dinamik destination + eventType),
  `service/OrderService.java` (saga başlatma), `dto/OrderResponse.java`, `controller/OrderController.java` (`GET /{id}`).
- `db/migration/V3__saga_outbox_routing.sql` (yeni).

#### subscription-service (boş iskeletten dolduruldu)
- `service/SubscriptionSagaService.java`, `consumer/SubscriptionCommandConsumer.java`, `saga/OutboxWriter.java`,
  `polling/OutboxPoller.java`, entity'ler (`Subscription`/`MsisdnPool`/`SimCard`/`OutboxEvent`/`OutboxStatus`/`ProcessedEvent`/`AuditLog`), repo'lar.
- `db/migration/V2__saga_participant.sql` (order_id + outbox/inbox/audit + MSISDN seed). `pom.xml` (+stream-kafka). `@EnableScheduling`.

#### payment-service (boş iskeletten dolduruldu)
- `service/PaymentSagaService.java`, `consumer/PaymentCommandConsumer.java`, `saga/OutboxWriter.java`,
  `polling/OutboxPoller.java`, entity'ler (`Payment`/`PaymentAttempt`/...), repo'lar.
- `db/migration/V2__saga_participant.sql`. `pom.xml` (+stream-kafka). `@EnableScheduling`.

#### billing & notification
- `consumer/OrderEventConsumer.java`, `service/OrderEventHandler.java` (OrderConfirmed/OrderCancelled).
- notification `db/migration/V3__order_cancelled_template.sql`.

#### config (config-server/configs)
- `order-service.yaml` (saga-replies consumer; `orderPlaced-out-0` kaldırıldı),
  `payment-service.yaml`/`subscription-service.yaml` (stream consumer eklendi),
  `billing-service.yaml`/`notification-service.yaml` (consumeOrderEvents).

---

### 12. Saga / Spring Cloud Stream notları

1. **Jackson 3** (`tools.jackson.databind.ObjectMapper`) - Spring Boot 4 ile gelir; serialize/deserialize unchecked exception fırlatır.
2. **StreamBridge dinamik destination** - komutlar için output binding tanımlanmadı; poller `destination` kolonundaki topic'e doğrudan gönderir.
3. **`eventType` header'ı güvenli okunur** - Kafka binder header'ı String ya da byte[] verebildiği için her iki durum da ele alınır.
4. **Flyway immutability** - payment/subscription'ın mevcut V1'i değiştirilmedi; saga şeması V2 olarak eklendi.
5. **Çoklu instance güvenli** - OutboxPoller'ın `findPublishable` sorgusu `FOR UPDATE SKIP LOCKED` kullanır (order/subscription/payment); her instance farklı PENDING satırlarını kilitleyip işler, çift publish olmaz.
6. **Sync publish** - Kafka producer `sync: true` (üç producer config'inde): `streamBridge.send` broker ack'ini bekler; ack gelmezse exception → satır PENDING kalır ve retry edilir. Aksi halde send `true` dönüp mesaj producer buffer'ında kaybolabilirdi (at-most-once).

---

### 13. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| order POST 201 ama status PENDING_PAYMENT'ta kalıyor | subscription/payment ayakta değil ya da Kafka bağlı değil. `logs/<servis>.log`, kafka-ui topic'leri. 2 dk sonra timeout CANCELLED yapar. |
| `MsisdnReservationFailed` | MSISDN havuzu boş. subscription V2 seed çalıştı mı? (`msisdn_pool` FREE satırları) |
| Reply işlenmiyor | `saga-replies` consumer binding'i (`order-service.yaml` `sagaReplies-in-0`) ve `spring.cloud.function.definition` kontrol. |
| Aynı iş iki kez | inbox eksik/yanlış. `processed_events` ve handler'daki `existsById` kontrolü. |
| Ödeme hep başarılı, compensation göremiyorum | Tutar 1000 TRY altında. >1000 TRY tarife sipariş et (§10). |
| Flyway checksum hatası | Mevcut migration'ları elle değiştirme; yeni `V_n` ekle. DB sıfırlamak için `docker compose down -v`. |

---

### 14. Kapsam ve sonraki adımlar

**Şu an kapsamda:**
- Onboarding saga'sı (orchestration): reserve → charge → activate, tam compensation (release/refund) + timeout.
- subscription & payment participant'ları (mock iş mantığı, gerçek state machine + idempotency).
- billing/notification terminal event reaksiyonu (saga dışı).
- Build: tüm modüller derleniyor (BUILD SUCCESS).

**İyileştirme adayları:**
- ~~OutboxPoller çoklu-instance güvenliği (`SELECT ... FOR UPDATE SKIP LOCKED`)~~ ✅ yapıldı (bkz. §12 not 5).
- ~~Feign çağrılarına Resilience4j circuit breaker + fallback~~ ✅ yapıldı (bkz. §12).
- Compensation ack'lerini bekleyip CANCELLED'a geçen iki-fazlı iptal (şu an fire-and-forget).
- Kafka DLQ + retry/backoff politikası.
- Aylık bill-run + `InvoiceGenerated → Payment` (recurring auto-pay) senaryosu.
- Subscription/payment için REST endpoint'leri + Swagger UI.

---

## CQRS (Command Query Responsibility Segregation) — Platform Deseni

Bu doküman, platform genelinde uygulanan **mediator tabanlı CQRS** yapısını anlatır.
Yazma (Command) ve okuma (Query) sorumlulukları ayrı model + handler'lara bölünür; controller'lar
handler'lara doğrudan değil, ortak bir **Mediator** üzerinden erişir (MediatR benzeri yaklaşım).

> Referans yapı: hocanın [turkcell-gygy-5/spring-cqrs](https://github.com/halitkalayci/turkcell-gygy-5/tree/master/spring-cqrs)
> projesindeki mediator + pipeline + feature-based (vertical slice) tasarımı esas alınmıştır.

---

### 1. Neden CQRS ve Neden Mediator?

- **Sorumlulukların ayrılması:** Yazma ve okuma yollarının iş mantığı izole edilir.
- **Performans/ölçeklenebilirlik:** Okuma yolları bağımsız optimize edilebilir (örn. Redis `@Cacheable`),
  yazma yolları cache'i geçersiz kılar (`@CacheEvict`).
- **Bakım kolaylığı:** Büyük `*ServiceImpl` sınıfları yerine her operasyona özel küçük handler'lar.
- **Mediator:** Controller yalnızca `Mediator`'a bağımlıdır; capraz kesitler (loglama, ileride
  authorization/validation) pipeline behavior'ları ile tek yerden eklenir.

---

### 2. Mimari Karar: Framework `common-lib`'te, Auto-Configuration ile

CQRS **altyapısı** (Mediator, pipeline, `Command/Query/Handler` arayüzleri) `common-lib`'e konur ve
Spring Boot **auto-configuration** ile tüm servislere dağıtılır — component-scan **değil**.

**Neden auto-config (sektör standardı):** Paylaşılan bir kütüphanenin bean'lerini tüketici servislere
dağıtmanın standart yolu Spring Boot starter/auto-config desenidir. Consumer'ı kütüphanenin iç paket
yapısına bağlamaz, koşullu (`@ConditionalOnMissingBean`) ve override edilebilir. Repo zaten bu deseni
kullanıyor (`ResourceServerSecurityAutoConfiguration`, `CommonOpenApiAutoConfiguration`, ...).

**Hibrit sonuç:**
- **Framework** → `common-lib` (`com.turkcell.commonlib.cqrs.*`), `CqrsAutoConfiguration` ile bean olur.
- **Feature'lar** (command/query/handler/mapper/rule) → her servisin **kendi** base paketinde,
  servisin normal component-scan'i ile bulunur.

Bu sayede henüz boş olan servisler (identity/usage/ticket) ileride kod aldığında ekstra kurulum
yapmadan `implements Command/Query` + `mediator.send(...)` ile CQRS'i kullanır.

---

### 3. Framework (`common-lib` / `com.turkcell.commonlib.cqrs`)

```text
common-lib/.../commonlib/cqrs/
├── Command.java                 # Command<R> belirtec arayuzu (R = donus tipi)
├── Query.java                   # Query<R> belirtec arayuzu
├── CommandHandler.java          # CommandHandler<C extends Command<R>, R> { R handle(C) }
├── QueryHandler.java            # QueryHandler<Q extends Query<R>, R> { R handle(Q) }
├── Mediator.java                # send(Command<R>) / send(Query<R>)
├── SpringMediator.java          # reflection ile handler cozer + pipeline calistirir
├── CqrsAutoConfiguration.java   # Mediator + LoggingBehavior bean'lerini saglar (auto-config)
└── pipeline/
    ├── PipelineBehavior.java        # handle(request, next) + supports(request)
    ├── RequestHandlerDelegate.java  # @FunctionalInterface -> zincir adimi
    ├── LoggingBehavior.java         # @Order(20), her istegi loglar (NotLoggableRequest haric)
    └── NotLoggableRequest.java      # loglanmamasi gereken istekler icin belirtec
```

`CqrsAutoConfiguration` `META-INF/spring/.../AutoConfiguration.imports` dosyasına eklidir.

#### `SpringMediator` — proxy-aware handler çözümü
Handler'lar `@Cacheable`/`@Transactional` ile **CGLIB proxy'lenebildiği** için, hocanın referansındaki
naif reflection (`TODO: Refactor` notlu) yerine gerçek tip
`AopProxyUtils.ultimateTargetClass(bean)` ile çözülür; dönen (proxy'li) bean üzerinde cache/transaction
advice'ı korunur. Eşleştirme sonucu handler singleton olduğu için `ConcurrentHashMap`'te cache'lenir.

Bu davranış izole bir testle doğrulanmıştır: `common-lib/src/test/.../cqrs/SpringMediatorTest.java`
(command/query eşleştirme + `@Cacheable` proxy + record-accessor SpEL key + "handler bulunamadı").

---

### 4. Servis İçi Feature Yapısı (vertical slice)

Her servis, kendi base paketi altında `application/features/<entity>/` yapısını kullanır:

```text
application/features/<entity>/
├── command/<action>/
│   ├── <Action>Command.java         # record ... implements Command<Response> (+ @Valid)
│   └── <Action>CommandHandler.java  # @Component implements CommandHandler<Command, Response>
├── query/<action>/
│   ├── <Action>Query.java           # record ... implements Query<Response>
│   └── <Action>QueryHandler.java    # @Component implements QueryHandler<Query, Response>
├── mapper/<Entity>Mapper.java       # entity <-> command/response donusumleri
└── rule/<Entity>BusinessRules.java  # is kurallari (opsiyonel)
```

Controller yalnızca `Mediator`'a bağımlıdır ve cevabı platform standardı `ApiResponse<T>` ile sarar:

```java
@PostMapping
@PreAuthorize("hasRole('CATALOG_ADMIN')")
public ApiResponse<TariffResponse> create(@Valid @RequestBody CreateTariffCommand command) {
    return ApiResponse.ok(mediator.send(command), "Tarife olusturuldu");
}
```

> Not: Hoca cevabı doğrudan döner; biz gateway/BFF/Feign kontratı gereği `ApiResponse<T>` zarfını korur,
> command'i ise hoca gibi doğrudan `@RequestBody` olarak bağlarız (ayrı `Create*Request` DTO'su yok).

---

### 5. Uygulanan Servisler

| Servis | Command | Query | Cache |
|---|---|---|---|
| **product-catalog-service** | `CreateTariffCommand` | `GetTariffByCodeQuery`, `ListTariffsQuery` | `@Cacheable` okuma, `@CacheEvict` yazma |
| **order-service** | `PlaceOrderCommand` (Feign doğrulama + saga başlatma, `@Transactional`) | `GetOrderQuery` (`@Transactional(readOnly)`) | — |
| **customer-service** | — (yazma endpoint'i yok) | `GetCustomerByIdQuery` (`@Cacheable`), `GetAllCustomersQuery` | `@Cacheable` okuma |

**Neden diğerleri değil:** subscription/payment (saf saga participant) ve billing/notification (saf Kafka
consumer) zaten event-handler yapısındadır; controller→command/query split'i uygulanmaz. identity/usage/ticket
şimdilik boş iskelettir — kod aldıklarında bu framework'ü kullanacaklardır (§6).

---

### 6. Yeni Bir Servise/Feature'a CQRS Ekleme

1. Servis zaten `common-lib`'e bağımlıysa framework hazırdır (auto-config).
2. `application/features/<entity>/...` altında command/query + handler'ları oluştur.
3. Command/Query için: `record X... implements Command<Resp>` / `Query<Resp>`.
4. Handler için: `@Component class XHandler implements CommandHandler<X, Resp>` (veya `QueryHandler`).
5. Controller'a `Mediator` inject et, `mediator.send(...)` çağır, `ApiResponse` ile sar.
6. Okuma yoğunsa handler'a `@Cacheable`, ilgili yazma handler'ına `@CacheEvict` ekle.

---

### 7. Test Edilmesi

- **Birim/izolasyon:** `SpringMediatorTest` (DB/Kafka/Redis gerektirmez) — `./mvnw -pl common-lib test`.
- **Uçtan uca:** Proje kök dizinindeki
  **[telco_crm_postman_collection.json](../telco_crm_postman_collection.json)** koleksiyonu. Endpoint'ler ve
  JSON gövdeleri değişmediği için mevcut istekler aynen çalışır.
