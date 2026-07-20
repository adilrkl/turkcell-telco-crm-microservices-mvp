# Operasyon — Deployment (CI/CD & Kubernetes) ve Observability

## Deployment — Paketleme, CI/CD & Kubernetes

Bu bölüm paketleme/deploy hattını anlatır: servis imajları (Dockerfile), GitHub Actions
CI test-gate'i ve Helm chart'ı. Faz 5 (secret/TLS sertleştirme) ve Faz 6 (prod
runtime, alerting, MinIO) bilinçli olarak kapsam dışıdır.

### 1. Servis imajları (paketleme)

14 Spring Boot servisi (config/eureka/gateway/bff + 10 iş servisi) **tek
parametrik [Dockerfile](../Dockerfile)**'dan çıkar. `common-lib` kütüphanedir,
imajı yoktur.

**Tasarım kararları:**
- **Layered jar (Jib değil):** Boot'un `tools` jarmode'u slim `app.jar` + `lib/`
  üretir; `lib/` ayrı imaj katmanı olduğundan bağımlılıklar değişmedikçe katman
  cache'ten gelir, kod değişiminde yalnızca küçük `app.jar` katmanı yenilenir.
- **Jar Docker dışında üretilir:** CI'da Maven cache'i, lokalde IDE build'i
  yeniden kullanılır; 14 serviste Docker içinde Maven koşturmak çok yavaş olurdu.
  [.dockerignore](../.dockerignore) allowlist'i sayesinde build context'e yalnızca
  `*/target/*.jar` gider.
- **Non-root + salt JRE** (`eclipse-temurin:21-jre-alpine`); `HEALTHCHECK`
  actuator health'e bakar (K8s'te probe'lar Helm'den gelir, compose/podman için).
- Her imaj kendi varsayılan portunu `ENV SERVER_PORT` ile taşır; runtime'da
  `-e SERVER_PORT=...` ile ezilebilir. JVM ayarı için `JAVA_TOOL_OPTIONS` kullan.

**Lokal build:**
```bash
export JAVA_HOME=<jdk-21>           # Windows: C:\Users\HP\jdks\jdk-21.0.11+10
./mvnw -B package -DskipTests       # jar'lar
scripts/build-images.sh             # 14 imaj -> telco-crm/<servis>:local
# tek servis:
docker build --build-arg SERVICE=order-service --build-arg PORT=8084 \
             -t telco-crm/order-service:local .
# hizli duman testi (compose altyapisi ayaktayken):
docker run --rm -p 8084:8084 -e KAFKA_BROKERS=host.docker.internal:9092 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5436/order_db \
  -e EUREKA_URL=http://host.docker.internal:8761/eureka/ \
  telco-crm/order-service:local
```
> Servis→port eşlemesinin tek doğruluk kaynağı [scripts/build-images.sh](../scripts/build-images.sh)
> içindeki `SERVICES` listesidir; Helm `values.yaml` aynı değerleri taşır.

### 2. CI — GitHub Actions ([.github/workflows/ci.yml](../.github/workflows/ci.yml))

| Job | Ne yapar | Gate |
|---|---|---|
| `backend` | `./mvnw clean verify` — 100 test, Testcontainers (Postgres/Kafka/Redis) runner'ın Docker'ında | PR + main |
| `frontend` | `npm ci` + typecheck + vitest (67 test) + prod build | PR + main |
| `helm` | `helm lint` + `helm template` render doğrulaması | PR + main |
| `images` | jar paketle + 14 imaj build; **yalnızca main push'unda** GHCR'a publish | PR'da build-only |

- İmaj adları: `ghcr.io/adilrkl/turkcell-telco-crm-microservices-mvp/<servis>:<kısa-sha>` + `:latest`.
- Publish için ek secret gerekmez (`GITHUB_TOKEN` + `packages: write`).
- Playwright E2E CI'da koşmaz (canlı 14 servis + Keycloak ister); lokalde `npm run e2e`.
- Testcontainers Linux runner'da ek ayar istemez (`~/.testcontainers.properties`
  yalnızca Windows/Docker Desktop içindir).

### 3. Kubernetes — Helm chart ([deploy/helm/telco-crm](../deploy/helm/telco-crm))

docx §5 hedefi karşılanır: **Deployment + Service (ClusterIP) + HPA +
readiness/liveness/startup probe + ConfigMap/Secret**. 14 servis tek generic
şablondan üretilir; servis listesi/portlar/env'ler `values.yaml`'dadır.

**Kapsam sınırı (bilinçli):** Chart yalnızca uygulama servislerini kurar.
Altyapı (10× PostgreSQL, Kafka, Redis, Keycloak, otel/tempo/loki/prometheus)
chart dışıdır — lokal denemede compose altyapısı, prod'da yönetilen
servisler/ayrı chart'lar. Adresler `platformEnv` ve
`services.*.env.SPRING_DATASOURCE_URL` ile verilir.

**Probe'lar:** `management.endpoint.health.probes.enabled=true` global config'te
zaten açık → `/actuator/health/{readiness,liveness}` hazır. Startup probe
5s×36=180s'e kadar tolerans tanır (JVM + Flyway + Kafka binder açılışı).

**HPA:** varsayılan olarak `gateway-server` (min 2) ve `order-service`'te açık;
CPU %70 hedefi. `metrics-server` gerektirir. Diğer servislerde
`services.<ad>.hpa.enabled=true` ile açılır (hepsi stateless — outbox/inbox +
`SKIP LOCKED` poller'lar yatay ölçeğe hazır, bkz. [roadmap.md](roadmap.md)).

**Kurulum:**
```bash
helm lint deploy/helm/telco-crm
helm template telco-crm deploy/helm/telco-crm | less   # kuru inceleme
helm install telco-crm deploy/helm/telco-crm -n telco-crm --create-namespace \
  --set image.tag=<sha>                                # :latest yerine sabit tag önerilir
kubectl get pods -n telco-crm -l app.kubernetes.io/part-of=telco-crm
```

**Ortam farkları için:** `-f my-values.yaml` ile `platformEnv` (Kafka/Keycloak/
observability adresleri), `dbCredentials.pass` ve datasource URL'leri ez.
`KEYCLOAK_ISSUER` JWT `iss` claim'iyle birebir eşleşmek zorunda — SPA dışarıdan
login oluyorsa Keycloak'ın dış host adı cluster içinden de çözülmelidir
(CoreDNS rewrite / external service).

**Sıralama:** İlk kurulumda config-server + eureka açılana kadar diğer pod'lar
birkaç restart atabilir; `optional:configserver:` + startup probe bunu tolere
eder, init-container sıralaması bilinçli eklenmedi (K8s idiyomu: crash-loop
yerine probe toleransı).

**Private registry:** GHCR paketi private kalırsa `image.pullSecrets`
(values.yaml'daki örnek komutla docker-registry secret'ı) verilir; lokal demo
buna ihtiyaç duymaz (imajlar cluster içine build edilir, aşağıya bkz.).

#### 3.1 Lokal K8s demosu — minikube'de uçtan uca

Chart'ı "kağıt üzerinde doğru"dan "çalışıyor"a taşıyan kurulum. Chart'ın
kapsam sınırı değişmez: altyapı yine chart dışıdır; demo için hafifletilmiş
kopyaları [deploy/k8s/demo-infra/](../deploy/k8s/demo-infra/) ham manifest'leriyle
**cluster içine** kurulur, adres farkları
[values-minikube.yaml](../deploy/helm/telco-crm/values-minikube.yaml) ile ezilir.

| Compose'daki hali | Demo'daki hali | Neden |
|---|---|---|
| 10 ayrı PostgreSQL | **tek** Postgres, 10 database + 10 kullanıcı (initdb) | RAM; kullanıcı/db adları birebir aynı, servisler farkı bilmez |
| Kafka `localhost:9092` advertise eder | Kafka `kafka:9092` advertise eder | pod içinde localhost = pod'un kendisi; cluster DNS adı şart |
| Keycloak `:8095`, realm bind-mount | Keycloak `keycloak:8080`, realm ConfigMap (tek kaynak: `docker/keycloak/`) | issuer `http://keycloak:8080/...` values ile birebir |
| Grafana/Tempo/Loki/Prometheus/otel | **kurulmaz** | `TRACE_SAMPLING=0.0` + Loki appender'ı yalnız `dev` profilinde |
| requests yok (host JVM) | 256Mi request / 768Mi limit + `-XX:MaxRAMPercentage=50` | rezervasyon laptop'a sığsın; taşan pod OOMKill ile görünür olsun |

**Kurulum (Git Bash):**
```bash
minikube start --cpus=4 --memory=8g   # ilk sefer; Docker Desktop'a en az bu kadar kaynak ver
kubectl config current-context        # "minikube" olmalı! (eski bir cluster'a bakıyorsa:
                                      #  kubectl config use-context minikube)
export JAVA_HOME=<jdk-21> && ./mvnw -B package -DskipTests
scripts/k8s-demo-up.sh                # imaj build (cluster içine) + altyapı + helm install
kubectl get pods -n telco-crm -w      # taze makinede 20-25 dk; pod başına 3-5 restart NORMAL
                                      # (altyapı imajları iner, postgres ilk init'te yavaştır,
                                      #  DB'li servisler onu bekleyip yeniden dener — MÜDAHALE ETME;
                                      #  ölçüldü: 2026-07-17 taze kurulum provası, uçtan uca ~40 dk)
```
> Docker Desktop K8s'te: `SKIP_DOCKER_ENV=true scripts/k8s-demo-up.sh`
> (imajlar zaten aynı daemon'da; metrics-server'ı elle kur).

**Doğrulama & gösteri egzersizleri:**
```bash
# 1) Self-healing: pod öldür, Deployment yenisini yaratsın
kubectl delete pod -n telco-crm -l app.kubernetes.io/name=customer-service
kubectl get pods -n telco-crm -w

# 2) HPA: CPU yükü bas (port-forward sonrası k6/hey/curl döngüsü), ölçek izle
kubectl get hpa -n telco-crm -w

# 3) Ölçek + Eureka: kopyalar isimle bulunmaya devam eder (prefer-ip-address)
kubectl scale deploy/usage-service -n telco-crm --replicas=3

# 4) Rolling update/rollback (imaj tag'i değiştirerek)
kubectl rollout status deploy/order-service -n telco-crm
kubectl rollout undo   deploy/order-service -n telco-crm

# 5) Node bakımı simülasyonu (çok node'lu başlatıldıysa: minikube start --nodes=2)
kubectl drain minikube-m02 --ignore-daemonsets --delete-emptydir-data

# API dumanı: gateway üzerinden (JWT için aşağıdaki issuer notu)
kubectl port-forward -n telco-crm svc/gateway-server 8888:8888
```

**Dışarıdan token almak (issuer eşleşmesi):** servisler `iss ==
http://keycloak:8080/realms/telco-crm` bekler → hosts dosyasına
`127.0.0.1 keycloak` ekle + `kubectl port-forward -n telco-crm svc/keycloak
8080:8080`, token'ı `http://keycloak:8080` üzerinden al (testuser/csruser —
realm import'la gelir). SPA'yı bağlamak istersen aynı yol + BFF port-forward
(9000) yeter; kalıcı çözüm (Ingress) Faz 5.

**Bilinçli demo sınırları:** veri kalıcı değil (emptyDir/AOF'suz — pod ölünce
Flyway/auto-create yeniden kurar), secret'lar düz metin demo değerleri,
Ingress/TLS yok, observability yok. Bunlar Faz 5/6 kalemleridir; demo'nun
amacı deploy mekanizmasını ve K8s davranışlarını (self-healing, HPA, rollout)
kanıtlamaktır. Temizlik: `scripts/k8s-demo-down.sh` (namespace'i siler).

**Demoda bulunan tuzaklar (kalıcı düzeltmeleri chart/manifest'te):**

| Bulgu | Kök neden | Düzeltme |
|---|---|---|
| config-server "git URI yok" ile çöktü | `platformEnv`'deki `SPRING_PROFILES_ACTIVE=prod`, servisin kendi `application.yaml`'ındaki `native` profilini ezdi → git backend'ine düştü | `values.yaml`'da config-server'a açıkça `native,prod` |
| config-server thread tükenmesi → probe timeout → kubelet SIGKILL | env'deki `SPRING_CONFIG_IMPORT` config-server'ın istemci istekleri için açtığı iç context'lerde de çalışıp kendine HTTP döngüsü kurdu | config-server'a boş `SPRING_CONFIG_IMPORT: ""` |
| toplu açılışta pod'lar "ölü" sayılıp öldürüldü | probe `timeoutSeconds` K8s default'u 1s; yük altındaki JVM 1s'de cevap veremiyor | probe timeout 3-5s + liveness `failureThreshold: 6` |
| açılışta restart fırtınası | 4 CPU'da 14 JVM aynı anda açılınca JVM başına ~0.25 core düşüyor, açılış 5-10 dk; 300s startup toleransı yetmedi | `values-minikube.yaml` startup toleransı 600s (120×5s); alternatif: servisleri 3-4'lü dalgalarla açmak |
| HPA order'ı 2'ye çıkarınca yeni kopya CrashLoop'a girdi (2026-07-17): "remaining connection slots are reserved for SUPERUSER" | 10 DB'li servis × Hikari default havuz 10 = 100 kalıcı bağlantı; Postgres default `max_connections=100` doluydu, rolling/HPA ek pod'u slot bulamadı | demo Postgres `max_connections=200` (tepe ihtiyaç ~140); kalıcı çözüm adayı: `configs/application.yaml`'da Hikari `max-pool-size`'ı düşürmek |

### 4. Bilinçli kararlar & sonraya bırakılanlar

- **Frontend imajı yok:** SPA dev'de Vite proxy ile çalışır; prod servis şekli
  (statik hosting vs BFF'ten servis) kullanıcı↔müşteri bağlantısı kararıyla
  birlikte Faz 6'da netleşecek ([frontend/README.md](../frontend/README.md) §13).
- **Typed-client kararı (Faz 4'te bağlandı):** üretilen client **commit'lenir**
  (`npm run generate:api` canlı stack ister; CI'da stack yok → build-time spec
  üretimi Faz 6'ya). FE typed-client geçişi FE track'inde ayrı iş.
- **Compose'a uygulama servisleri eklenmedi:** compose altyapı içindir
  (README çalışma düzeni); tam-konteyner lokal denemesi istenirse imajlar +
  `SPRING_CONFIG_IMPORT`/`KAFKA_BROKERS` env'leriyle eklenebilir.
- **Secret'lar demo:** `dbCredentials.pass=secret` compose ile aynı; gerçek
  secret yönetimi (Vault/external-secrets, `{cipher}`) **Faz 5**.
- **Ingress/TLS yok:** dışa açma + TLS termination **Faz 5** (gateway
  sertleştirme ile birlikte).
- **Alerting/HPA yük doğrulaması:** **Faz 6** (k6 + Alertmanager).

---

## Observability (Gözlemlenebilirlik) — Telco CRM Platform

Bu doküman, projeye eklenen **sektörel seviye observability katmanını** anlatır:
ne olduğu, ne işe yaradığı, nasıl çalıştığı, nasıl ayağa kaldırıldığı ve nasıl doğrulandığı.

> Stack: **Grafana LGTM + OpenTelemetry** — Java 21 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1

---

### 1. Ne yaptık ve neden?

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

### 2. Mimari

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

### 3. Bileşenler ve portlar

#### Observability stack (Docker)

| Bileşen | Image | Port | Not |
|---|---|---|---|
| Grafana | grafana/grafana:11.5.1 | **3000** | UI — admin/admin (anonim Admin de açık) |
| Prometheus | prom/prometheus:v3.1.0 | **9090** | metrik scrape + sorgu |
| Tempo | grafana/tempo:2.7.1 | **3200** | trace backend (query API) |
| Loki | grafana/loki:3.4.2 | **3100** | log backend (push + query) |
| OTel Collector | otel/opentelemetry-collector-contrib:0.119.0 | **4317/4318** | OTLP gRPC / HTTP girişi |

#### Uygulama servisleri (host) — Prometheus scrape hedefleri

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

### 4. Nasıl başlatılır?

#### Ön koşullar
- Docker çalışıyor, Java 21, Maven (wrapper `./mvnw` var).

#### Adımlar

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

#### Tam sıfırlama (isteğe bağlı, "temiz makine" testi)
```bash
docker compose down -v && docker compose up -d   # TÜM volume'ları siler (veri gider, Flyway+realm re-import kendini onarır)
./mvnw clean install -DskipTests && ./scripts/start-all.sh
```

---

### 5. Nasıl kullanılır / doğrulanır?

#### Grafana — http://localhost:3000  (admin / admin)
- **Dashboards → Telco CRM → "Service Overview"**: request rate, **p95 latency (hedef <300ms)**, 5xx hata oranı, JVM heap, target UP sayısı.
- **Explore → Tempo**: trace ara (`{}` ya da `{ resource.service.name = "order-service" }`), span'e tıkla → ilgili loglara geç.
- **Explore → Loki**: `{app="order-service"}` → logdaki `traceId=...` linkinden trace'e geç.

#### Prometheus — http://localhost:9090/targets
- `spring-services` job'unda tüm hedefler **UP (yeşil)** olmalı.

#### Komut satırından hızlı doğrulama
```bash
# Metrics: target durumu
curl -s "http://localhost:9090/api/v1/targets?state=active" | grep -o '"health":"up"' | wc -l   # -> 15

# Traces: son trace'ler
curl -s -G "http://localhost:3200/api/search" --data-urlencode 'q={}' --data-urlencode 'limit=10'

# Logs: Loki'ye log basan servisler
curl -s "http://localhost:3100/loki/api/v1/label/app/values"
```

#### Uçtan uca dağıtık trace demosu (sipariş akışı)
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

### 6. Hangi dosyalar değişti / eklendi?

#### Bağımlılıklar
- `pom.xml` (root): tüm modüllere ortak —
  `micrometer-registry-prometheus` (metrics), **`spring-boot-starter-opentelemetry`** (traces),
  `com.github.loki4j:loki-logback-appender` (logs).
- `order-service/pom.xml`: `io.github.openfeign:feign-micrometer` (Feign trace propagation).

#### Konfigürasyon
- `config-server/src/main/resources/configs/application.yaml` (**global**): prometheus exposure,
  tracing sampling + export, OTLP endpoint, OTLP metrik push kapalı, Kafka observation, `loki.url`, log korelasyon deseni.
- `config-server/.../configs/gateway-server.yaml`, `eureka-server/.../application.yml`,
  `config-server/.../application.yaml`: prometheus exposure (override / config-client olmayan servisler).
- `common-lib/src/main/resources/logback-spring.xml`: loki4j appender (dev profilinde) + konsol.

#### Docker / provisioning
- `docker-compose.yml`: 5 observability servisi + volume'lar.
- `docker/otel/collector-config.yaml`, `docker/tempo/tempo.yaml`, `docker/loki/loki.yaml`,
  `docker/prometheus/prometheus.yaml`, `docker/grafana/provisioning/{datasources,dashboards}/...`

#### Scriptler
- `scripts/start-all.sh`, `scripts/stop-all.sh`

---

### 7. Spring Boot 4 tuzakları (önemli)

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

### 8. Sorun giderme

| Belirti | Olası neden / çözüm |
|---|---|
| Prometheus target **DOWN (500/404)** | Servis **eski jar** ile çalışıyor. `./mvnw install` + `./scripts/start-all.sh` |
| Target **connection refused** | Servis ayakta değil. `logs/<servis>.log`'a bak. |
| Tempo'da **trace yok** | `traceId` boş mu? → `spring-boot-starter-opentelemetry` eksik olabilir (bkz. §7). Collector ayakta mı (`docker compose ps`)? |
| Loki'de **log yok** | Servis `dev` profilinde mi? (loki4j sadece dev'de aktif). Yalnızca **common-lib'e bağlı** servisler push eder. |
| bff/gateway logları Loki'de yok | Tasarım gereği: bu servisler common-lib'e bağlı değil (traces+metrics yine var). |
| Container config değişti | Sadece o container'ı yeniden başlat: `docker compose restart <servis>`. |

---

### 9. Kapsam ve sonraki adımlar

**Şu an kapsamda:** 3 ayak çalışıyor; gateway→order→Feign→customer/catalog dağıtık trace; Grafana korelasyonu; p95 histogram.

**İyileştirme adayları:**
- İş metrikleri / custom span'ler (servis içleri doldukça): ör. "tarifeye göre sipariş", saga adımları.
- Kafka span devamlılığı (`order-events` → billing/notification) — `spring.kafka.*.observation-enabled` açık, uçtan uca doğrulanabilir.
- Grafana alerting (p95 > 300ms, 5xx artışı) + Loki/Prometheus alert kuralları.
- Üretimde: sampling'i düşür (`TRACE_SAMPLING`), Tempo/Loki retention'ı artır, kalıcı storage (S3/MinIO).
- bff/gateway loglarını da Loki'ye almak istenirse: bu modüllere de paylaşılan logback eklenebilir.
