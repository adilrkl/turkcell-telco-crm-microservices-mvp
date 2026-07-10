# syntax=docker/dockerfile:1
# ======================================================================
# Ortak servis imaji (Faz 4) - monorepo'daki TUM Spring Boot servisleri
# tek parametrik Dockerfile'dan cikar (README Faz 4: "layered veya Jib"
# -> layered secildi; Jib ek plugin/registry konfigu getirirdi).
#
# Jar Docker DISINDA uretilir (CI'da mvn cache'i, lokalde IDE build'i
# yeniden kullanilir; Docker icinde maven calistirmak 14 serviste cok yavas):
#   ./mvnw -B package -DskipTests
# Sonra servis basina (veya hepsi icin scripts/build-images.sh):
#   docker build --build-arg SERVICE=order-service --build-arg PORT=8084 \
#                -t telco-crm/order-service:local .
# Build context'i .dockerignore allowlist'i sayesinde yalnizca */target/*.jar tasir.
# ======================================================================

ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# --- 1. asama: Boot jar'ini ayristir ------------------------------------
# "tools" jarmode slim app.jar + lib/ uretir (manifest Class-Path lib'i
# gosterir). lib/ ayri COPY katmani olur: bagimliliklar degismedikce
# imaj katmani cache'ten gelir, kod degisiminde yalniz app.jar katmani yenilenir.
FROM ${JRE_IMAGE} AS extract
ARG SERVICE
WORKDIR /build
COPY ${SERVICE}/target/${SERVICE}-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted

# --- 2. asama: calisma imaji (yalniz JRE, non-root) ----------------------
FROM ${JRE_IMAGE}
ARG SERVICE
ARG PORT=8080
LABEL org.opencontainers.image.title="${SERVICE}" \
      org.opencontainers.image.source="https://github.com/adilrkl/turkcell-telco-crm-microservices-mvp"

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Once bagimliliklar (buyuk, nadir degisir), sonra uygulama (kucuk, sik degisir)
COPY --from=extract /build/extracted/lib/ lib/
COPY --from=extract /build/extracted/app.jar app.jar

# server.port tum servislerde ${SERVER_PORT:<default>} okur; imaj kendi
# default'unu tasir, runtime'da -e SERVER_PORT=... ile ezilebilir.
ENV SERVER_PORT=${PORT}
EXPOSE ${PORT}
USER app

# JVM ayari gerekirse JAVA_TOOL_OPTIONS env'i ver (JVM otomatik okur):
#   -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# K8s'te saglik probe'lari Helm chart'indan gelir; compose/podman icin HEALTHCHECK:
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
