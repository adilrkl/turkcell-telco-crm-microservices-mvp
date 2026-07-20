#!/usr/bin/env bash
# ======================================================================
# Tum servis imajlarini tek parametrik Dockerfile'dan uretir.
# On kosul: jar'lar uretilmis olmali -> ./mvnw -B package -DskipTests
#
# Kullanim:
#   scripts/build-images.sh                                  # telco-crm/<servis>:local
#   REGISTRY=ghcr.io/adilrkl/turkcell-telco-crm-microservices-mvp \
#     TAG=abc1234 EXTRA_TAG=latest PUSH=true scripts/build-images.sh   # CI publish
#
# SERVICES listesi service:port eslemesinin tek dogruluk kaynagidir
# (Helm values.yaml ayni port degerlerini tasir; degistirirsen ikisini de guncelle).
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY="${REGISTRY:-telco-crm}"
TAG="${TAG:-local}"
EXTRA_TAG="${EXTRA_TAG:-}"
PUSH="${PUSH:-false}"

SERVICES=(
  config-server:8889
  eureka-server:8761
  gateway-server:8888
  bff-server:9000
  identity-service:8081
  customer-service:8082
  product-catalog-service:8083
  order-service:8084
  subscription-service:8085
  usage-service:8086
  billing-service:8087
  payment-service:8088
  notification-service:8089
  ticket-service:8090
)

for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"; port="${entry##*:}"
  image="${REGISTRY}/${svc}:${TAG}"
  jar=("$svc"/target/"$svc"-*.jar)
  if [[ ! -f "${jar[0]}" ]]; then
    echo "HATA: ${svc} icin jar bulunamadi - once ./mvnw -B package -DskipTests calistir." >&2
    exit 1
  fi
  echo "==> ${image} (port ${port})"
  docker build --build-arg SERVICE="${svc}" --build-arg PORT="${port}" -t "${image}" .
  if [[ -n "${EXTRA_TAG}" ]]; then
    docker tag "${image}" "${REGISTRY}/${svc}:${EXTRA_TAG}"
  fi
  if [[ "${PUSH}" == "true" ]]; then
    docker push "${image}"
    [[ -n "${EXTRA_TAG}" ]] && docker push "${REGISTRY}/${svc}:${EXTRA_TAG}"
  fi
done

echo "Tamam: ${#SERVICES[@]} imaj (${REGISTRY}/*:${TAG})."
