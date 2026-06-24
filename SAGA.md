# Saga Orchestration - Telco CRM Platform

Bu doküman, projeye eklenen **Saga orchestration** katmanını anlatır:
ne olduğu, neden bu tasarımın seçildiği, hangi servislerin katıldığı, mesajlaşmanın nasıl kurulduğu,
nasıl başlatılıp test edildiği ve hata/compensation yollarının nasıl tetiklendiği.

> Stack: **Saga (orchestration) + Transactional Outbox/Inbox** - Java 21 · Spring Boot 4.0.6 · Spring Cloud Stream (Kafka) 2025.1.1 · PostgreSQL · Flyway

---

## 1. Ne yaptık ve neden?

"Yeni hat siparişi" (onboarding) tek bir servise sığmayan, **birden fazla servisin DB'sini** değiştiren bir iş akışıdır:
ödeme alınır, MSISDN (numara) ayrılır, abonelik aktive edilir. Dağıtık bir transaction'ı tek bir `@Transactional`
ile yönetemeyiz (servisler ayrı DB - database-per-service). Bu yüzden **Saga pattern** kullanıldı:

| Kavram | Ne sağlar? |
|---|---|
| Saga | Dağıtık transaction'ı, her adımın bir **geri-al (compensation)** adımı olduğu bir state machine ile yönetir |
| Orchestration | Merkezî bir koordinatör (order-service) adımları sırayla tetikler ve durumu `saga_states`'te tutar |
| Transactional Outbox | DB değişikliği + mesaj yayını **atomik** olur (aynı commit) |
| Inbox (idempotent consumer) | Aynı mesaj iki kez gelirse iş iki kez yapılmaz (`processed_events`) |

### Tasarım kararı: dokümana sadık + ER reserve-first

MVP analiz dokümanı (§9.2) **kanonik saga akışını** tanımlar. Kararımız buna hizalandı:

- **Billing saga'da DEĞİL.** Postpaid faturalama onboarding'de fatura kesmez (önce kullan, ay sonu bill-run ile faturalan).
  Payment, onboarding'de **order tutarını** çeker (fatura değil). billing yalnızca event'e reaksiyonla `bill_cycle` açar.
- **Payment, saga adımı.** order → payment → subscription sıralaması doküman §9.2 ile aynı.
- **reserve-first (bilinçli iyileştirme).** ER'deki `MSISDN_POOL.status (FREE/RESERVED/ALLOCATED)` + `reserved_until`
  rezervasyon için tasarlanmış. Önce numarayı rezerve ederiz, ödeme gelince ALLOCATED yaparız. Böylece ödeme
  başarısız olursa **para hiç hareket etmez** (refund yerine sadece release) - en temiz compensation.

---

## 2. Mimari

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

## 3. Topic topolojisi

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

## 4. Event kontratları (`common-lib`)

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

## 5. Bileşenler ve servisler

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

## 6. Mesajlaşma deseni: outbox + raw bytes + eventType dispatch

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

## 7. Order durum makinesi

`orders.status` (MVP §FR-11) ve `saga_states.current_step`:

| order.status | saga_states.current_step | Tetikleyen |
|---|---|---|
| PENDING_PAYMENT | STARTED | Sipariş alındı, ReserveMsisdn yayınlandı |
| PENDING_PAYMENT | MSISDN_RESERVED | MsisdnReserved geldi, ChargePayment yayınlandı |
| PAID | PAYMENT_COMPLETED | PaymentCompleted geldi, ActivateSubscription yayınlandı |
| FULFILLED | COMPLETED | SubscriptionActivated geldi, OrderConfirmed yayınlandı |
| CANCELLED | CANCELLED | Herhangi bir failure / timeout → compensation |

---

## 8. Nasıl başlatılır?

### Ön koşullar
- Docker çalışıyor, Java 21, `./mvnw`.

### Adımlar
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

## 9. Nasıl test edilir / doğrulanır?

### 9.1 Mutlu yol (başarılı sipariş)
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

### 9.2 Doğrulama noktaları
- **Kafka** (kafka-ui http://localhost:8080): `subscription-commands`, `payment-commands`, `saga-replies`, `order-events` topic'lerinde mesajlar.
- **order DB** (5436): `saga_states.current_step = COMPLETED`, `orders.status = FULFILLED`.
- **subscription DB** (5437): `subscriptions.status = ACTIVE`, `msisdn_pool` ilgili numara `ALLOCATED`, yeni `sim_cards` satırı, `audit_log` kayıtları.
- **payment DB** (5440): `payments.status = PAID`, `payment_attempts` APPROVED.
- **billing DB** (5439): yeni `bill_cycles` satırı.
- **notification DB** (5441): `ORDER_CONFIRMED` notification.
- **Trace**: Grafana (http://localhost:3000) → sipariş trace'i Kafka hop'larıyla birlikte.

---

## 10. Demo failure knob'ları (compensation testi)

Compensation yolunu uçtan uca görmek için iki deterministik tetikleyici:

| Senaryo | Nasıl tetiklenir | Sonuç |
|---|---|---|
| **Ödeme reddi** | Aylık ücreti **> 1000 TRY** olan bir tarife sipariş et | `PaymentFailed` → `ReleaseMsisdn` (MSISDN FREE'ye döner, abonelik CANCELLED) → order **CANCELLED** |
| **Aktivasyon hatası** | Tarife kodu **`_FAIL`** ile biten bir sipariş ver | `SubscriptionActivationFailed` → `RefundPayment` + `ReleaseMsisdn` → order **CANCELLED** |
| **Timeout** | Bir participant'ı kapat ve sipariş ver | 2 dk sonra `SagaTimeoutScheduler` iptal + ulaşılan adıma göre compensation |

Eşik `PaymentSagaService.FAIL_THRESHOLD`, `_FAIL` kuralı `SubscriptionSagaService.activate` içinde.

---

## 11. Hangi dosyalar değişti / eklendi?

### common-lib
- `com/turkcell/commonlib/saga/` - 15 event record + `SagaTopics` + `SagaHeaders` (yeni).
- `com/turkcell/commonlib/event/OrderPlacedEvent.java` - **kaldırıldı** (yerine OrderConfirmed/OrderCancelled).

### order-service
- `saga/OrderSagaOrchestrator.java`, `saga/SagaReplyConsumer.java`, `saga/SagaTimeoutScheduler.java`,
  `saga/OutboxWriter.java`, `saga/SagaContext.java`, `saga/SagaSteps.java`, `saga/OrderStatus.java` (yeni).
- `entity/SagaState.java`, `entity/ProcessedEvent.java`, `repository/SagaStateRepository.java`,
  `repository/ProcessedEventRepository.java` (yeni).
- `entity/OutboxEvent.java` (+`destination`), `polling/OutboxPoller.java` (dinamik destination + eventType),
  `service/OrderService.java` (saga başlatma), `dto/OrderResponse.java`, `controller/OrderController.java` (`GET /{id}`).
- `db/migration/V3__saga_outbox_routing.sql` (yeni).

### subscription-service (boş iskeletten dolduruldu)
- `service/SubscriptionSagaService.java`, `consumer/SubscriptionCommandConsumer.java`, `saga/OutboxWriter.java`,
  `polling/OutboxPoller.java`, entity'ler (`Subscription`/`MsisdnPool`/`SimCard`/`OutboxEvent`/`OutboxStatus`/`ProcessedEvent`/`AuditLog`), repo'lar.
- `db/migration/V2__saga_participant.sql` (order_id + outbox/inbox/audit + MSISDN seed). `pom.xml` (+stream-kafka). `@EnableScheduling`.

### payment-service (boş iskeletten dolduruldu)
- `service/PaymentSagaService.java`, `consumer/PaymentCommandConsumer.java`, `saga/OutboxWriter.java`,
  `polling/OutboxPoller.java`, entity'ler (`Payment`/`PaymentAttempt`/...), repo'lar.
- `db/migration/V2__saga_participant.sql`. `pom.xml` (+stream-kafka). `@EnableScheduling`.

### billing & notification
- `consumer/OrderEventConsumer.java`, `service/OrderEventHandler.java` (OrderConfirmed/OrderCancelled).
- notification `db/migration/V3__order_cancelled_template.sql`.

### config (config-server/configs)
- `order-service.yaml` (saga-replies consumer; `orderPlaced-out-0` kaldırıldı),
  `payment-service.yaml`/`subscription-service.yaml` (stream consumer eklendi),
  `billing-service.yaml`/`notification-service.yaml` (consumeOrderEvents).

---

## 12. Saga / Spring Cloud Stream notları

1. **Jackson 3** (`tools.jackson.databind.ObjectMapper`) - Spring Boot 4 ile gelir; serialize/deserialize unchecked exception fırlatır.
2. **StreamBridge dinamik destination** - komutlar için output binding tanımlanmadı; poller `destination` kolonundaki topic'e doğrudan gönderir.
3. **`eventType` header'ı güvenli okunur** - Kafka binder header'ı String ya da byte[] verebildiği için her iki durum da ele alınır.
4. **Flyway immutability** - payment/subscription'ın mevcut V1'i değiştirilmedi; saga şeması V2 olarak eklendi.
5. **Tek instance** - OutboxPoller'da `SKIP LOCKED` yok (demo için yeterli; çoklu-instance için sonraki adım).

---

## 13. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| order POST 201 ama status PENDING_PAYMENT'ta kalıyor | subscription/payment ayakta değil ya da Kafka bağlı değil. `logs/<servis>.log`, kafka-ui topic'leri. 2 dk sonra timeout CANCELLED yapar. |
| `MsisdnReservationFailed` | MSISDN havuzu boş. subscription V2 seed çalıştı mı? (`msisdn_pool` FREE satırları) |
| Reply işlenmiyor | `saga-replies` consumer binding'i (`order-service.yaml` `sagaReplies-in-0`) ve `spring.cloud.function.definition` kontrol. |
| Aynı iş iki kez | inbox eksik/yanlış. `processed_events` ve handler'daki `existsById` kontrolü. |
| Ödeme hep başarılı, compensation göremiyorum | Tutar 1000 TRY altında. >1000 TRY tarife sipariş et (§10). |
| Flyway checksum hatası | Mevcut migration'ları elle değiştirme; yeni `V_n` ekle. DB sıfırlamak için `docker compose down -v`. |

---

## 14. Kapsam ve sonraki adımlar

**Şu an kapsamda:**
- Onboarding saga'sı (orchestration): reserve → charge → activate, tam compensation (release/refund) + timeout.
- subscription & payment participant'ları (mock iş mantığı, gerçek state machine + idempotency).
- billing/notification terminal event reaksiyonu (saga dışı).
- Build: tüm modüller derleniyor (BUILD SUCCESS).

**İyileştirme adayları:**
- OutboxPoller çoklu-instance güvenliği (`SELECT ... FOR UPDATE SKIP LOCKED`).
- Feign çağrılarına Resilience4j circuit breaker + fallback.
- Compensation ack'lerini bekleyip CANCELLED'a geçen iki-fazlı iptal (şu an fire-and-forget).
- Kafka DLQ + retry/backoff politikası.
- Aylık bill-run + `InvoiceGenerated → Payment` (recurring auto-pay) senaryosu.
- Subscription/payment için REST endpoint'leri + Swagger UI.
