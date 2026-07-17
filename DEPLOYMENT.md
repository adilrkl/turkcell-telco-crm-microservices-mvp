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

**Private registry:** GHCR paketi private kalırsa `image.pullSecrets`
(values.yaml'daki örnek komutla docker-registry secret'ı) verilir; lokal demo
buna ihtiyaç duymaz (imajlar cluster içine build edilir, aşağıya bkz.).

### 3.1 Lokal K8s demosu — minikube'de uçtan uca

Chart'ı "kağıt üzerinde doğru"dan "çalışıyor"a taşıyan kurulum. Chart'ın
kapsam sınırı değişmez: altyapı yine chart dışıdır; demo için hafifletilmiş
kopyaları [deploy/k8s/demo-infra/](deploy/k8s/demo-infra/) ham manifest'leriyle
**cluster içine** kurulur, adres farkları
[values-minikube.yaml](deploy/helm/telco-crm/values-minikube.yaml) ile ezilir.

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
