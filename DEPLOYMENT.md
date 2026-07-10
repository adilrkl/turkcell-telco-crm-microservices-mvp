# Deployment (Faz 4) — Paketleme, CI/CD & Kubernetes

Bu doküman Faz 4 çıktısını anlatır: servis imajları (Dockerfile), GitHub Actions
CI test-gate'i ve Helm chart'ı. Faz 5 (secret/TLS sertleştirme) ve Faz 6 (prod
runtime, alerting, MinIO) bilinçli olarak kapsam dışıdır.

## 1. Servis imajları (paketleme)

14 Spring Boot servisi (config/eureka/gateway/bff + 10 iş servisi) **tek
parametrik [Dockerfile](Dockerfile)**'dan çıkar. `common-lib` kütüphanedir,
imajı yoktur.

**Tasarım kararları:**
- **Layered jar (Jib değil):** Boot'un `tools` jarmode'u slim `app.jar` + `lib/`
  üretir; `lib/` ayrı imaj katmanı olduğundan bağımlılıklar değişmedikçe katman
  cache'ten gelir, kod değişiminde yalnızca küçük `app.jar` katmanı yenilenir.
- **Jar Docker dışında üretilir:** CI'da Maven cache'i, lokalde IDE build'i
  yeniden kullanılır; 14 serviste Docker içinde Maven koşturmak çok yavaş olurdu.
  [.dockerignore](.dockerignore) allowlist'i sayesinde build context'e yalnızca
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
> Servis→port eşlemesinin tek doğruluk kaynağı [scripts/build-images.sh](scripts/build-images.sh)
> içindeki `SERVICES` listesidir; Helm `values.yaml` aynı değerleri taşır.

## 2. CI — GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml))

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

## 3. Kubernetes — Helm chart ([deploy/helm/telco-crm](deploy/helm/telco-crm))

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
`SKIP LOCKED` poller'lar yatay ölçeğe hazır, bkz. README Faz 2).

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

## 4. Bilinçli kararlar & sonraya bırakılanlar

- **Frontend imajı yok:** SPA dev'de Vite proxy ile çalışır; prod servis şekli
  (statik hosting vs BFF'ten servis) kullanıcı↔müşteri bağlantısı kararıyla
  birlikte Faz 6'da netleşecek ([FRONTEND.md](FRONTEND.md) §13).
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
