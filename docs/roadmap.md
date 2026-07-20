# Yol Haritası ve Durum

> README'den taşındı (2026-07-19): fazlar, DEVAM NOKTASI, Faz 3.5 tablosu ve backlog burada yaşar.

## Sonraki Adımlar

Temel mimari oturdu: config, service discovery, gateway + BFF, Keycloak güvenlik, event-driven
Outbox/Inbox, Saga orchestration, mediator-tabanlı CQRS, rate limiting ve observability entegre.
Frontend de artık bu repodadır (monorepo, `frontend/`); mimari kararları için [frontend/README.md](../frontend/README.md).

### 📍 DEVAM NOKTASI (son güncelleme: 2026-07-13 — **Faz 4 TAMAMLANDI + lokal K8s demosu**)

**Durum:** Faz 1 ✅ · Faz 2 ✅ · Faz 3 ilerliyor · **Faz 3.5: G1–G9 ✅** · FE Sprint 3–5 ✅ (67 FE test yeşil) ·
**Faz 4 ✅** (parametrik layered Dockerfile + `scripts/build-images.sh` ile 14 servis imajı; GitHub Actions CI:
backend `mvn verify` + frontend vitest/build + helm lint PR gate'i, main push'unda GHCR'a imaj publish;
Helm chart: Deployment+Service+HPA+probe+ConfigMap/Secret — ayrıntı ve kararlar [operations.md](operations.md)) ·
**Lokal K8s demosu ✅** (minikube'de uçtan uca: `scripts/k8s-demo-up.sh` — cluster içi demo altyapı
[deploy/k8s/demo-infra/](../deploy/k8s/demo-infra/) + [values-minikube.yaml](../deploy/helm/telco-crm/values-minikube.yaml);
runbook + gösteri egzersizleri [operations.md](operations.md) §3.1) ·
Sıradaki backend işi: **Faz 5** (secret sertleştirme) veya Faz 3 kalanları (coverage gate, contract test).
> ✅ Sağlamlık düzeltmesi (Faz 4 hazırlığı): gateway `contextLoads` testi gerçek `localhost:6379` Redis'ine
> bağlanıyordu (compose kapalıyken/CI'da kırmızı); Testcontainers Redis'e geçirildi — suite artık compose
> altyapısı OLMADAN uçtan uca yeşil (CI'ın çalışma önkoşulu buydu).
> ℹ️ Dev notu (FE Sprint 4): SPA `:5173`'ten login → Keycloak → SPA akışı için `vite.config.ts` proxy'de `/oauth2 /login /logout` **`changeOrigin: false`** olmalı (BFF `redirect_uri`'yi Host'tan üretir; `:9000` olursa login sonrası Whitelabel 404'e düşer). Keycloak `telco-bff` client'ında `http://localhost:5173/*` redirect kayıtlı.
> ⚠️ Davranış değişikliği (G9): müşteri oluşturmada **TCKN/VKN artık zorunlu ve algoritmik doğrulanıyor** (INDIVIDUAL→TCKN, CORPORATE→VKN; geçersizse 422). Demo seed müşterileri SQL ile eklendiği için etkilenmez.
> ℹ️ Karar (G7): outbox **common-lib'e çıkarılmadı** — servis bağımsızlığı korunuyor, her servis kendi kopyasını taşır (nihai; bir daha sorulmayacak).
> ⚠️ Davranış değişikliği (G3): **yeni müşteri artık `PENDING` doğar**; sipariş verebilmesi için belge
> yükleme + `POST /api/customers/{id}/kyc/approve` (ADMIN) gerekir. Demo seed müşterisi ACTIVE kalır.

**Öncelik değişikliği (2026-07-04):** MVP analiz dokümanı (`telco-crm-microservices-mvp ... .docx`) ile satır satır
kıyas yapıldı. Sonuç: derinlikte doküman aşıldı (outbox/inbox, DLQ, test, observability), **genişlikte boşluklar var**.
Karar: önce **Faz 3.5 — docx kapsam boşlukları** (aşağıdaki G1–G9 tablosu) kapatılacak.
**Saga sertleştirme backlog'a alındı** — docx gereksinimi değil (FR-10/12 karşılandı ve `OrderSagaIntegrationTest` ile kanıtlı);
yalnızca timeout süresini config'e alma ufak işi uygun bir backend PR'ına iliştirilecek.

**Çalışma düzeni:** İki paralel track, ayrık dosya ağaçları → iki PR aynı anda açık olabilir, conflict çıkmaz.
Kural: [frontend/README.md](../frontend/README.md) FE track'ine, bu README'nin yol haritası backend track'ine aittir.

| Sıradaki iş | Track A — Frontend (`frontend/`) | Track B — Backend |
|---|---|---|
| **✅ (tamamlananlar)** | **Sprint 4 ✅** Billing (fatura + kalem + PDF **G6**) + Subscriptions (yaşam döngüsü **G4**) · **Sprint 5 ✅** kalan G-UI: **G5** sipariş iptal, **G9** müşteri sil, **G1** kota kartı, **G3** KYC onay/red + belge; 67 FE test yeşil | **Faz 3.5 ✅** G1–G9 · **Faz 4 ✅** Dockerfile + CI + Helm ([operations.md](operations.md)) |
| **1 (buradan devam)** | Typed-client (`generate:api`) geçişi — karar bağlandı: üretilen client **commit'lenir** (aşağıya bkz.) · **ertelenen:** G9 bildirim tercihleri UI (`GET/PUT /api/notifications/preferences/{userId}`) — userId Keycloak `sub`'i, kullanıcı↔müşteri bağlantısı yok; anlamlı yer için o karar gerekli | **Faz 5** (secret sertleştirme) veya Faz 3 kalanları (JaCoCo coverage gate CI'a eklenmeye hazır; contract/newman testi) |

**Açık kararlar (bloklamıyor):** CUSTOMER self-servisi için kullanıcı↔müşteri bağlantısı (Keycloak `sub` ≠ `customerId`;
ilk FE sürümü CSR/ADMIN odaklı — [frontend/README.md](../frontend/README.md) §13) · JaCoCo coverage eşiği (kapsam büyüyünce; CI artık
`verify` koştuğu için eşik tek satırlık iş) · Faz 5/6 kalemleri.
> ✅ Karar (Faz 4): typed-client için **üretilen client commit'lenir** — `npm run generate:api` canlı stack ister,
> CI'da stack yok; build-time spec üretimi (springdoc maven plugin) MinIO/Faz 6 dalgasına bırakıldı.

**Kaldığın yerden hızlı başlatma:**
```bash
docker compose up -d                      # altyapı (keycloak realm değiştiyse: --force-recreate keycloak)
./mvnw clean install -DskipTests          # JAVA_HOME = JDK 21 olmalı
# servisleri sırayla başlat (bkz. "Başlangıç" bölümü) — 14 servis
cd frontend && npm install && npm run dev # http://localhost:5173 → "Giriş yap" → csruser/test12345
./mvnw test                               # 100 test (Kafka'lı saga IT dahil; Docker açık olmalı)
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
| **G6 ✅** | **Fatura PDF** — TAMAMLANDI | FR-23 | `GET /api/billing/invoices/{id}/pdf` (BILLING_ADMIN/CSR/ADMIN) — OpenPDF ile istek anında bellekte üretilir, `application/pdf` + attachment döner (başlık/kalemler/KDV/genel toplam; para birimi bill-cycle'dan, yoksa TRY); saklama yok → `invoices.pdf_ref` MinIO ile Faz 6'da dolacak. Bilinçli sapma: docx'in "PDF notification'a gönderilir" kısmı e-posta eki gerektirir → object storage ile Faz 6'ya; `InvoiceGenerated` e-postası (G2) zaten kesim bilgisini taşıyor |
| **G7 ✅** | **Ticket otomasyonu** — TAMAMLANDI | FR-32/33 | `POST /api/tickets` artık açılışta otomasyon uygular (`TicketOpeningPolicy`): önceliğe göre `slaDueAt` (URGENT 4s / HIGH 8s / MEDIUM 24s / LOW 72s; `slaDueAt` artık client'tan alınmaz) + kategoriye göre ekip yönlendirmesi (`team`: BILLING/PAYMENT→BILLING_TEAM, TECHNICAL/NETWORK→TECH_TEAM, SALES→SALES_TEAM, diğer→GENERAL_TEAM — basit auto-assign; bireysel CSR ataması mevcut `assigned_to`+assign endpoint'iyle elle sürer). `TicketOpened` domain event'i (event-carried state: category/priority/team/slaDueAt) transactional outbox → yeni `ticket-events` topic → notification `TICKET_OPENED` SMS'i atar (V8, inbox idempotent). ticket-service'e outbox altyapısı eklendi (V4: `team` kolonu + `outbox_events`; **common-lib'e çıkarılmadı** — bağımsızlık kararı). Publish-only servis (consume yok) |
| **G8 ✅** | **Ödeme dunning retry** — TAMAMLANDI | FR-27 | başarısız fatura otomatik tahsilatı (`InvoicePaymentFailed`) artık bir `dunning_schedules` planı açar; payment-service scheduler'ı vadesi gelen planları `payment.dunning.intervals` (varsayılan 24/72/168 saat, origin'e göre offset) ile re-charge eder. Başarılı retry → `InvoicePaid` (billing PAYMENT_FAILED→PAID, notification "ödendi") + plan RESOLVED; tüm denemeler biterse EXHAUSTED. Mock PSP tek kaynağa çekildi (`PaymentGateway`, eşik `payment.fail-threshold`); charge/attempt/audit `InvoiceChargeProcessor`+`PaymentAuditWriter`'a ortaklaştırıldı. Config: `payment.dunning.intervals` (Duration listesi; demo için `10s,20s,30s`), `poll-interval-ms`, `demo-recover-on-retry` (transient toparlanmayı simüle eder). Kilitli tarama (`FOR UPDATE SKIP LOCKED`), plan başına bir fatura (invoice_id UNIQUE). Not: `SlaBreached`/exhausted-bildirimi kapsam dışı — plan EXHAUSTED'da audit+log kalır |
| **G9 ✅** | **Küçükler** — TAMAMLANDI | FR-01/04/30 | **TCKN/VKN** (`TurkishIdentityValidator` algoritmik checksum; `CustomerBusinessRules` create/update'te tip'e göre doğrular — INDIVIDUAL→TCKN, CORPORATE→VKN; geçersiz/eksik → 422 `CUSTOMER_INVALID`; kimlik artık zorunlu). **Soft-delete** (`DELETE /api/customers/{id}`, CSR/ADMIN): satır kalır, `deletedAt` damgalanır; entity `@SQLRestriction("deleted_at is null")` ile silinen müşteri tüm sorgulardan düşer → order-service Feign 404 alır, sipariş açılamaz; cache evict edilir. **Opt-in/out** (`notification_preferences` V9, user+channel unique): tüm 6 handler ortak `NotificationDispatcher`'a çekildi (dedup + tercih kontrolü + yazım tek yerde); opt-out'ta bildirim `SKIPPED` yazılır ama event yine processed işaretlenir; `GET`/`PUT /api/notifications/preferences/{userId}` (CSR/ADMIN). Satır yoksa varsayılan opt-in |

**Bilinçli scope-out (yapılmayacak — docx §6.2 zaten dışarıda tutuyor ya da MVP için ağır):**
Addon/VAS kataloğu + paket değişikliği/addon siparişi (FR-05'in kalanı, FR-09'un kalanı) · tarife versiyonlama (FR-08) ·
MNP (FR-16, §6.2 scope-out) · prepaid/top-up (§6.2) · PII şifreleme + Vault (Faz 5'te) · kurumsal müşteri akışları (§6.2).

**Docx'ten bilinçli sapmalar (eksik değil, karar):** Keycloak + BFF (docx'te Keycloak "opsiyonel", baz senaryo
gateway-behind-trust + custom JWT idi; tam OIDC daha doğru ve refresh rotation'ı Keycloak hallediyor) ·
saga **orchestration** (docx §9.2 şeması choreography-vari ama §8.3 "Saga ile orchestrate" der; architecture.md'de gerekçeli) ·
versiyonsuz `/api/**` (docx `/api/v1`) · `ApiResponse` zarfı (docx RFC 7807) · portlar 8081–8090 (docx 9001–9010) ·
el yazımı mapper (docx MapStruct) · `Idempotency-Key`/`Correlation-Id` header'ları yerine event-inbox idempotency +
OTel trace korelasyonu · FE SPA (docx'te scope-out — BFF konusunu sergilemek için bilinçli eklendi).

### ✅ Tamamlananlar
- **Faz 4 — Paketleme, CI/CD & deployment** — parametrik layered Dockerfile + 14 imaj script'i; GitHub Actions
  test-gate (backend/frontend/helm) + GHCR publish; Helm chart (Deployment/Service/HPA/probe/ConfigMap/Secret).
  Gateway `contextLoads` testi Testcontainers Redis'e geçirildi → suite compose altyapısı olmadan yeşil (CI önkoşulu).
  Bkz. [operations.md](operations.md).
- **Abonelik yaşam döngüsü (G4, Faz 3.5)** — suspend/reactivate/terminate REST işlemleri (CSR/ADMIN, ilk yazma endpoint'leri; oluşturma yolu saga'da kalır); terminate MSISDN'i havuza döndürür + SIM'i kapatır; audit artık actor'lu (`AuditWriter`); üç domain event mevcut subscription outbox'ından `subscription-events`'e, notification SMS'e çevirir (V7). Migration gerekmedi (`suspended_at` V1'den beri vardı, entity'ye bağlandı).
- **KYC mini akışı (G3, Faz 3.5)** — yeni müşteri PENDING doğar; adres/belge API'leri (fileRef mock); ADMIN onay/red durum makinesi (onay şartı ≥1 belge); `CustomerKYCApproved` → `customer-events` (customer outbox V3) → notification SMS (V6). Not: `CustomerMapper` artık `createdAt`'i damgalar (örtük NOT NULL hatası giderildi).
- **Fatura bildirimleri (G2, Faz 3.5)** — bill-run `InvoiceGenerated` yayınlar; payment reply'ları müşteri/tutar taşır; notification `invoice-events`'ten üç fatura event'ini de EMAIL (mock) bildirimine çevirir (V5 şablonları + inbox IT).
- **Kota zinciri + CDR simülatörü (G1, Faz 3.5)** — `quotas`/`subscription_entitlements` (usage V4-V5) + usage-service outbox → `quota-events`; eşik SMS'leri (notification V4 şablonları) + `pending_charges` → bill-run aşım kalemi (billing V4). Aşım birim fiyatları billing config'te (`billing.overage.*`; ingest anında dondurulur). CDR simülatörü dev-profili endpoint'idir; kayıtlar gerçek Kafka yolundan akar.
- **Saga orchestration** — order orchestrator (`saga_states`) + reserve→ödeme→aktivasyon + compensation + timeout. Bkz. [architecture.md](architecture.md).
- **subscription / payment** saga akışına participant olarak dahil (outbox/inbox + `audit_log`).
- **Rate limiting** — gateway'de Bucket4j + Redis; user (JWT `sub`) / IP başına 100 req/dk; 429 + `Retry-After` + `X-RateLimit-*`. Bkz. §10.
- **CQRS (mediator)** — `common-lib` framework + auto-config; product-catalog / order / customer feature'ları. Bkz. [architecture.md](architecture.md).

### ✅ Faz 1 — Domain'i tamamla (boş servisler → CQRS ile) — TAMAMLANDI
İskelet halindeki üç servis CQRS vertical-slice yapısıyla dolduruldu (PR #10–#12), izlenen yol
[architecture.md](architecture.md) §6'daki şablondur (Flyway + entity/repo → feature handler'lar → controller yalnızca `Mediator`'a bağlı):
- ✅ **identity-service** — kullanıcı profili (auth Keycloak'ta kalır; burada profil verisi): me/get/list/update/sync feature'ları.
- ✅ **usage-service** — CDR/kullanım kaydı: Kafka'dan usage event consume (inbox idempotency) + dönem/abonelik bazlı agregasyon query'leri.
- ✅ **ticket-service** — destek talebi CRUD + durum makinesi (`OPEN→IN_PROGRESS→RESOLVED→CLOSED`) + yorum/atama; CSR rolü.

> Not: `subscription/payment` (saga participant) ve `billing/notification` (saf Kafka consumer) event-handler
> yapısındadır; bunlara controller→command/query split'i **uygulanmaz** ([architecture.md](architecture.md) §5).

### Faz 2 — Dayanıklılık & veri tutarlılığı
- ✅ **Resilience4j** — Feign çağrılarına (`order → customer / product-catalog`) circuit breaker + retry + timeout + fallback; 4xx devreyi saymaz, down/timeout → 503 `SERVICE_UNAVAILABLE`. Bkz. §12.
- ✅ **OutboxPoller çoklu-instance güvenliği** — `SELECT ... FOR UPDATE SKIP LOCKED` (order/subscription/payment) → yatay ölçekte çift publish yok.
- ✅ **Kafka DLQ + retry** — consumer hatalarında 3 deneme (üslü backoff) + `error.<topic>.<group>` dead-letter topic; zehirli mesaj izolasyonu (null `eventId`/bozuk payload artık sonsuz redelivery yapmaz). Tüm consumer'lara ortak (`application.yaml`).
- ✅ **Recurring billing** — aylık bill-run (`SKIP LOCKED`) + fatura kesimi + `ChargeInvoiceCommand → payment` auto-pay + `invoice-events` reply → invoice `PAID/PAYMENT_FAILED`. Bkz. §13.
- ✅ **FE hazırlık API'leri** — BFF `/api/me` + 401 sözleşmesi; subscription/billing okuma API'leri; order list; customer sayfalama/arama/create/update. Bkz. §14 ve [frontend/README.md](../frontend/README.md).
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

### ✅ Faz 4 — Paketleme, CI/CD & deployment — TAMAMLANDI (2026-07-09)
Ayrıntı ve kararlar: [operations.md](operations.md)
- ✅ **Dockerfile** — tek parametrik layered imaj (Boot `tools` jarmode: `lib/` ayrı katman; non-root, JRE-alpine,
  `HEALTHCHECK`); 14 servis `scripts/build-images.sh` ile (`common-lib` kütüphane, imajı yok). Jar Docker dışında
  üretilir (CI maven cache'i); `.dockerignore` allowlist'i context'e yalnızca jar'ları taşır.
- ✅ **CI pipeline** (GitHub Actions) — PR gate: backend `mvn verify` (Testcontainers) + frontend
  (typecheck/vitest/build) + `helm lint`; `images` job'ı PR'da build-only, main push'unda GHCR'a publish
  (`ghcr.io/<owner>/<repo>/<servis>:<kısa-sha>` + `:latest`; ek secret gerekmez).
- ✅ **Kubernetes / Helm** — docx §5 hedefi: generic şablondan 14× Deployment + Service + HPA (gateway/order
  varsayılan açık) + `readiness/liveness/startup` probe + ConfigMap (platform env) / Secret (demo DB parolası).
  Altyapı bilinçli chart dışı (compose lokal, prod'da yönetilen servisler); `helm lint`+`template` CI'da.

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
