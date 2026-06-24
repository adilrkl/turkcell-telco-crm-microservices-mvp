# Observability (Gözlemlenebilirlik) — Telco CRM Platform

Bu doküman, projeye eklenen **sektörel seviye observability katmanını** anlatır:
ne olduğu, ne işe yaradığı, nasıl çalıştığı, nasıl ayağa kaldırıldığı ve nasıl doğrulandığı.

> Stack: **Grafana LGTM + OpenTelemetry** — Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1

---

## 1. Ne yaptık ve neden?

Mikroservis mimarisinde bir istek birden çok servisten geçer
(`gateway → order → Feign → customer + catalog → Kafka → billing/notification`).
Bir şey yavaşladığında veya patladığında tek bir servisin loguna bakmak yetmez.
Bu yüzden **observability'nin üç ayağını** kurduk — her biri farklı bir soruyu yanıtlar:

| Ayak | Soruyu yanıtlar | Araç |
|---|---|---|
| **Metrics** | "Sistem nasıl gidiyor? p95 latency, hata oranı?" | Micrometer + **Prometheus** + **Grafana** |
| **Traces** | "Bu istek hangi servislerden geçti, nerede yavaşladı?" | Micrometer Tracing → OpenTelemetry → **Tempo** |
| **Logs** | "Tam olarak ne oldu, hata mesajı ne?" | loki4j → **Loki** |

Üçü `traceId` ile birbirine bağlıdır: **metrik = uyarı, trace = konum, log = kök neden.**

---

## 2. Mimari

Uygulamalar **HOST'ta** çalışır (`java -jar` / `mvnw`), tüm altyapı **Docker'da**.

```
                    ┌─────────────────────────────────────────────┐
   HOST             │                  DOCKER                      │
 (Spring app'ler)   │                                              │
                    │                                              │
  /actuator/prometheus  ◄──scrape── [ Prometheus :9090 ]           │
                    │                      │                       │
  OTLP/HTTP :4318 ──┼──► [ OTel Collector ]│──OTLP──► [ Tempo :3200 ]
                    │         :4317/:4318  │                       │
  loki4j push ──────┼──► [ Loki :3100 ]    │                       │
                    │         │            │                       │
                    │         └────────────┴───► [ Grafana :3000 ] │
                    │              (Prometheus + Tempo + Loki)      │
                    └─────────────────────────────────────────────┘
```

- **Metrics:** Prometheus, host'taki her servisin `/actuator/prometheus` ucunu `host.docker.internal:<port>` üzerinden **scrape** eder.
- **Traces:** Uygulama OTLP/HTTP ile `:4318`'e gönderir → OTel Collector batch'ler → Tempo'ya (OTLP gRPC) iletir.
- **Logs:** Uygulama (loki4j logback appender) doğrudan Loki'ye `:3100` push eder.
- **Grafana:** Üçünü tek pencerede gösterir; Tempo↔Loki **korelasyonu** kuruludur (trace'ten loga, logdaki `traceId`'den trace'e geçiş).

> **Neden OTel Collector?** Sektörel desen: uygulamalar tek bir giriş noktasına gönderir, backend'i (Tempo/Jaeger/bulut) Collector tarafında değiştirebilirsin — uygulama koduna dokunmadan.

---

## 3. Bileşenler ve portlar

### Observability stack (Docker)

| Bileşen | Image | Port | Not |
|---|---|---|---|
| Grafana | grafana/grafana:11.5.1 | **3000** | UI — admin/admin (anonim Admin de açık) |
| Prometheus | prom/prometheus:v3.1.0 | **9090** | metrik scrape + sorgu |
| Tempo | grafana/tempo:2.7.1 | **3200** | trace backend (query API) |
| Loki | grafana/loki:3.4.2 | **3100** | log backend (push + query) |
| OTel Collector | otel/opentelemetry-collector-contrib:0.119.0 | **4317/4318** | OTLP gRPC / HTTP girişi |

### Uygulama servisleri (host) — Prometheus scrape hedefleri

| Servis | Port | Servis | Port |
|---|---|---|---|
| eureka-server | 8761 | order-service | 8084 |
| config-server | 8889 | subscription-service | 8085 |
| gateway-server | 8888 | usage-service | 8086 |
| bff-server | 9000 | billing-service | 8087 |
| identity-service | 8081 | payment-service | 8088 |
| customer-service | 8082 | notification-service | 8089 |
| product-catalog-service | 8083 | ticket-service | 8090 |

---

## 4. Nasıl başlatılır?

### Ön koşullar
- Docker çalışıyor, Java 21, Maven (wrapper `./mvnw` var).

### Adımlar

```bash
# 1) Tüm altyapıyı kaldır (postgres'ler + kafka + redis + keycloak + LGTM stack)
docker compose up -d

# 2) Tüm modülleri derle (taze jar'lar)
./mvnw clean install -DskipTests

# 3) Tüm Spring servislerini bağımlılık sırasında başlat
#    (config-server → eureka → iş servisleri + gateway + bff)
./scripts/start-all.sh

# Durdurmak için:
./scripts/stop-all.sh
```

- `scripts/start-all.sh`: her servisi `java -jar` ile arka planda başlatır, logları `logs/<servis>.log`'a yazar,
  `/actuator/health` 200 olana kadar bekler. Bellek için varsayılan `JAVA_OPTS=-Xmx320m` (override edilebilir).
- `scripts/stop-all.sh`: port bazlı olarak host'taki servisleri durdurur (Docker'a dokunmaz).

### Tam sıfırlama (isteğe bağlı, "temiz makine" testi)
```bash
docker compose down -v && docker compose up -d   # TÜM volume'ları siler (veri gider, Flyway+realm re-import kendini onarır)
./mvnw clean install -DskipTests && ./scripts/start-all.sh
```

---

## 5. Nasıl kullanılır / doğrulanır?

### Grafana — http://localhost:3000  (admin / admin)
- **Dashboards → Telco CRM → "Service Overview"**: request rate, **p95 latency (hedef <300ms)**, 5xx hata oranı, JVM heap, target UP sayısı.
- **Explore → Tempo**: trace ara (`{}` ya da `{ resource.service.name = "order-service" }`), span'e tıkla → ilgili loglara geç.
- **Explore → Loki**: `{app="order-service"}` → logdaki `traceId=...` linkinden trace'e geç.

### Prometheus — http://localhost:9090/targets
- `spring-services` job'unda tüm hedefler **UP (yeşil)** olmalı.

### Komut satırından hızlı doğrulama
```bash
# Metrics: target durumu
curl -s "http://localhost:9090/api/v1/targets?state=active" | grep -o '"health":"up"' | wc -l   # -> 15

# Traces: son trace'ler
curl -s -G "http://localhost:3200/api/search" --data-urlencode 'q={}' --data-urlencode 'limit=10'

# Logs: Loki'ye log basan servisler
curl -s "http://localhost:3100/loki/api/v1/label/app/values"
```

### Uçtan uca dağıtık trace demosu (sipariş akışı)
```bash
# 1) Keycloak'tan CUSTOMER token al
TOKEN=$(curl -s -X POST http://localhost:8095/realms/telco-crm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=telco-bff \
  -d client_secret=kVqT3nP9rL7wX2mB6dF4hJ8sZ1cY5gA \
  -d username=testuser -d password=test12345 | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# 2) Gateway üzerinden sipariş ver
curl -X POST http://localhost:8888/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","tariffCode":"TARIFE_M"}'
```
Grafana → Explore → Tempo'da bu isteğin **tek bir trace içinde** şu zinciri ürettiğini görürsün:
```
gateway-server → order-service → (Feign) customer-service
                              └→ (Feign) product-catalog-service
```
(Doğrulandı: **25 span / 4 servis** tek trace'te.)

---

## 6. Hangi dosyalar değişti / eklendi?

### Bağımlılıklar
- `pom.xml` (root): tüm modüllere ortak —
  `micrometer-registry-prometheus` (metrics), **`spring-boot-starter-opentelemetry`** (traces),
  `com.github.loki4j:loki-logback-appender` (logs).
- `order-service/pom.xml`: `io.github.openfeign:feign-micrometer` (Feign trace propagation).

### Konfigürasyon
- `config-server/src/main/resources/configs/application.yaml` (**global**): prometheus exposure,
  tracing sampling + export, OTLP endpoint, OTLP metrik push kapalı, Kafka observation, `loki.url`, log korelasyon deseni.
- `config-server/.../configs/gateway-server.yaml`, `eureka-server/.../application.yml`,
  `config-server/.../application.yaml`: prometheus exposure (override / config-client olmayan servisler).
- `common-lib/src/main/resources/logback-spring.xml`: loki4j appender (dev profilinde) + konsol.

### Docker / provisioning
- `docker-compose.yml`: 5 observability servisi + volume'lar.
- `docker/otel/collector-config.yaml`, `docker/tempo/tempo.yaml`, `docker/loki/loki.yaml`,
  `docker/prometheus/prometheus.yaml`, `docker/grafana/provisioning/{datasources,dashboards}/...`

### Scriptler
- `scripts/start-all.sh`, `scripts/stop-all.sh`

---

## 7. Spring Boot 4 tuzakları (önemli)

1. **Tracing autoconfig ayrı modülde.** SB4'te actuator autoconfig modüllere bölündü.
   `micrometer-tracing-bridge-otel` **tek başına yetmez** — observation üretilir ama span üretilmez
   (request logunda `traceId` boş kalır, Collector 0 span alır). Çözüm:
   **`spring-boot-starter-opentelemetry`** (içinde `spring-boot-micrometer-tracing-opentelemetry`
   + `spring-boot-opentelemetry` autoconfig + OTLP exporter gelir).

2. **Property isimleri taşındı:**
   - `management.otlp.tracing.endpoint` → `management.opentelemetry.tracing.export.otlp.endpoint`
   - `management.tracing.enabled` → `management.tracing.export.enabled`

3. **OTLP metrik push'u kapat:** starter `micrometer-registry-otlp` getirir; metrikleri Prometheus *scrape* ettiği için
   `management.otlp.metrics.export.enabled: false`.

---

## 8. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| Prometheus target **DOWN (500/404)** | Servis **eski jar** ile çalışıyor. `./mvnw install` + `./scripts/start-all.sh` |
| Target **connection refused** | Servis ayakta değil. `logs/<servis>.log`'a bak. |
| Tempo'da **trace yok** | `traceId` boş mu? → `spring-boot-starter-opentelemetry` eksik olabilir (bkz. §7). Collector ayakta mı (`docker compose ps`)? |
| Loki'de **log yok** | Servis `dev` profilinde mi? (loki4j sadece dev'de aktif). Yalnızca **common-lib'e bağlı** servisler push eder. |
| bff/gateway logları Loki'de yok | Tasarım gereği: bu servisler common-lib'e bağlı değil (traces+metrics yine var). |
| Container config değişti | Sadece o container'ı yeniden başlat: `docker compose restart <servis>`. |

---

## 9. Kapsam ve sonraki adımlar

**Şu an kapsamda:** 3 ayak çalışıyor; gateway→order→Feign→customer/catalog dağıtık trace; Grafana korelasyonu; p95 histogram.

**İyileştirme adayları:**
- İş metrikleri / custom span'ler (servis içleri doldukça): ör. "tarifeye göre sipariş", saga adımları.
- Kafka span devamlılığı (`order-events` → billing/notification) — `spring.kafka.*.observation-enabled` açık, uçtan uca doğrulanabilir.
- Grafana alerting (p95 > 300ms, 5xx artışı) + Loki/Prometheus alert kuralları.
- Üretimde: sampling'i düşür (`TRACE_SAMPLING`), Tempo/Loki retention'ı artır, kalıcı storage (S3/MinIO).
- bff/gateway loglarını da Loki'ye almak istenirse: bu modüllere de paylaşılan logback eklenebilir.
