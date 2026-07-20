# Telco CRM Platform

Spring Boot 4.0.6 ve Spring Cloud 2025.1.1 tabanlı, çok modüllü Maven mikroservis projesi
(monorepo: backend + `frontend/` SPA). Config Server, Keycloak, Redis cache, Kafka
Transactional Outbox/Inbox, OpenFeign, Saga orchestration, CQRS, BFF ve Observability
tek platformda entegre edilmiştir.

## Ekip

- Adil Arkalı (https://github.com/adilrkl)
- Erol Koçoğlu (https://github.com/ErolKocoglu)
- Ayşe Ulaşlı (https://github.com/aaayseee)
- Eren Eray Yılmaz (https://github.com/erenerayyilmaz)

## Teknoloji Stack'i

- **Java 21** · **Maven** (multi-module)
- **Spring Boot 4.0.6** — `webmvc` starter (servlet stack)
- **Spring Cloud 2025.1.1** — Netflix Eureka, Gateway (MVC), Config, OpenFeign, Stream (Kafka binder)
- **PostgreSQL 16** — servis başına ayrı veritabanı (database-per-service), **Flyway** migration
- **Redis 7** — Spring Cache + gateway rate limiting
- **Apache Kafka 4.2 (KRaft)** — Spring Cloud Stream ile event akışı
- **Keycloak 26.1** — OAuth2/OIDC kimlik sağlayıcı (tek IdP)
- **Springdoc OpenAPI** — servis bazlı OpenAPI spec + Swagger UI
- **Micrometer + OpenTelemetry + Grafana LGTM** — metrics, tracing, log korelasyonu
- **Docker Compose** — yerel altyapı; **Docker + Helm + GitHub Actions** — imajlar, K8s chart'ı, CI ([docs/operations.md](docs/operations.md))
- **React + TypeScript (Vite) + Ant Design** — SPA ([frontend/README.md](frontend/README.md))

## Servisler ve Portlar

| Servis | Port | Açıklama |
|---|---|---|
| eureka-server | 8761 | Service registry |
| config-server | 8889 | Spring Cloud Config (native backend) |
| gateway-server | 8888 | API Gateway (Spring Cloud Gateway MVC) + rate limiting |
| bff-server | 9000 | Backend-for-Frontend (session + OAuth2 login + TokenRelay) |
| identity-service | 8081 | Profil servisi (auth Keycloak'ta) |
| customer-service | 8082 | Müşteri yönetimi (Redis cache) |
| product-catalog-service | 8083 | Ürün/tarife kataloğu (Redis cache) |
| order-service | 8084 | Sipariş + saga orchestrator + OpenFeign |
| subscription-service | 8085 | Abonelik (saga participant) |
| usage-service | 8086 | Kullanım/CDR + kota |
| billing-service | 8087 | Faturalama + bill-run |
| payment-service | 8088 | Ödeme (saga participant) + dunning |
| notification-service | 8089 | Bildirim |
| ticket-service | 8090 | Destek/talep |

Altyapı giriş noktaları (Docker): **Keycloak 8095** (admin/admin, realm `telco-crm`),
**kafka-ui 8080**, **pgAdmin 5151**, **Grafana 3000**, **Prometheus 9090**.
Diğer altyapı portları (PostgreSQL 5433–5442, Kafka 9092, Redis 6379, Tempo/Loki/OTel)
[docker-compose.yml](docker-compose.yml)'dedir.

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
Alternatif: `./scripts/start-all.sh` tüm servisleri sırayla başlatır (`./scripts/stop-all.sh` durdurur).

### 4. Frontend'i çalıştır (opsiyonel)
```bash
cd frontend
npm install        # ilk kurulumda
npm run dev        # Vite dev server -> http://localhost:5173
```
Vite dev server `/api`, `/oauth2`, `/login`, `/logout` isteklerini BFF'e (9000)
proxy'ler; bu yüzden BFF ayakta olmalı. Giriş: Keycloak üzerinden
`testuser/test12345` (CUSTOMER) veya `csruser/test12345` (CSR/ADMIN).
Detay: [frontend/README.md](frontend/README.md).

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
Bu tek çağrı **saga'yı** başlatır: token doğrulanır → Feign ile müşteri/fiyat doğrulama →
`ReserveMsisdn → ChargePayment → ActivateSubscription` (Kafka, outbox/inbox) →
order `FULFILLED`, `OrderConfirmed` → billing (bill_cycle) + notification (welcome).
Detaylı akış ve compensation senaryoları: [docs/architecture.md](docs/architecture.md).

### Doğrulama
- Eureka: <http://localhost:8761>
- Config: `curl http://localhost:8889/order-service/dev`
- Saga: `GET http://localhost:8084/api/orders/<id>` → birkaç sn içinde `FULFILLED`;
  `_FAIL` ile biten tarife kodu → compensation → `CANCELLED`
- Kafka: kafka-ui <http://localhost:8080> → `subscription-commands` / `payment-commands` / `saga-replies` / `order-events`
- Grafana: <http://localhost:3000> → Telco CRM dashboard / Explore (trace ↔ log korelasyonu)
- 403: `testuser` ile `POST /api/catalog/tariffs` → 403 (sadece `CATALOG_ADMIN`)
- BFF login: <http://localhost:9000/oauth2/authorization/keycloak>
- Swagger UI: customer <http://localhost:8082/swagger-ui.html> ·
  catalog <http://localhost:8083/swagger-ui.html> · order <http://localhost:8084/swagger-ui.html>

## Mimari

- **Database-per-Service** — her mikroservis kendi PostgreSQL şeması
- **Config Server** — merkezi config (native backend)
- **API Gateway + BFF** — gateway yönlendirme + rate limiting; BFF session/login katmanı
- **Service Discovery** — Netflix Eureka
- **Güvenlik** — Keycloak (OAuth2/OIDC), tüm servisler JWT resource-server
- **Event-Driven** — Transactional Outbox/Inbox + Kafka (Spring Cloud Stream), retry + DLQ
- **Saga Orchestration** — order-service orchestrator; reserve → ödeme → aktivasyon, compensation'lı
- **CQRS** — mediator tabanlı (common-lib auto-config); feature-based handler'lar
- **Senkron çağrı** — OpenFeign + Resilience4j circuit breaker
- **Cache** — Redis (okuma yoğun servisler)
- **Observability** — Micrometer/OTel + Prometheus, Grafana, Tempo, Loki

Tasarım kararları ve ayrıntılar: [docs/architecture.md](docs/architecture.md).

## Dokümantasyon

| Doküman | İçerik |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Entegre yapılar (config/Keycloak/Redis/Kafka/Feign/BFF/rate-limit/billing), Saga orchestration, CQRS |
| [docs/operations.md](docs/operations.md) | Paketleme (Dockerfile), CI (GitHub Actions), Helm/K8s, minikube demosu, Observability kurulum + sorun giderme |
| [docs/roadmap.md](docs/roadmap.md) | Durum, yol haritası, backlog ve bilinçli kapsam kararları |
| [frontend/README.md](frontend/README.md) | FE mimarisi, stack gerekçeleri, BFF entegrasyonu, sayfa↔endpoint eşlemesi |

## Proje Yapısı

```
telco-crm-platform/
├── pom.xml                       # parent pom
├── docker-compose.yml            # postgres'ler + pgadmin + kafka + redis + keycloak + observability stack
├── Dockerfile                    # tüm servisler için parametrik layered imaj
├── scripts/                      # start/stop-all, build-images, k8s-demo-up/down
├── .github/workflows/ci.yml      # CI: backend+frontend+helm gate, main'de GHCR imaj publish
├── deploy/helm/telco-crm/        # K8s chart: Deployment+Service+HPA+probe+ConfigMap/Secret
├── deploy/k8s/demo-infra/        # minikube demo altyapısı (tek postgres, kafka, keycloak)
├── docs/                         # architecture / operations / roadmap
├── docker/                       # keycloak realm, grafana/prometheus/otel/tempo/loki config
├── common-lib/                   # ApiResponse, JWT converter, saga event kontratları, CQRS mediator, autoconfig
├── config-server/                # Spring Cloud Config (native) + configs/ ağacı
├── eureka-server/                # service registry
├── gateway-server/               # API gateway + rate limiting
├── bff-server/                   # BFF (oauth2 login + TokenRelay)
├── identity-service/             # profil (auth Keycloak'ta)
├── customer-service/             # müşteri (Redis, KYC)
├── product-catalog-service/      # tarife kataloğu (Redis)
├── order-service/                # sipariş + saga orchestrator
├── subscription-service/         # abonelik (saga participant)
├── usage-service/                # kullanım + kota
├── billing-service/              # faturalama + bill-run
├── payment-service/              # ödeme (saga participant) + dunning
├── notification-service/         # bildirim
├── ticket-service/               # destek/talep
└── frontend/                     # React SPA (Vite)
```

## Test

```bash
./mvnw test                # backend (Testcontainers: Postgres/Kafka/Redis — Docker açık olmalı)
cd frontend && npm test    # vitest
```
