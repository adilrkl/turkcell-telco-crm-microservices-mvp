#!/usr/bin/env bash
# ======================================================================
# Lokal K8s demosu — tek komutla uctan uca kurulum (DEPLOYMENT.md §3.1).
#
# On kosullar:
#   - minikube ayakta:  minikube start --cpus=4 --memory=8g
#   - jar'lar uretilmis: ./mvnw -B package -DskipTests   (JAVA_HOME=JDK21)
#
# Ne yapar (sirasiyla):
#   1. Imajlari minikube'un Docker'ina build eder (image load'dan hizli:
#      save/load tar kopyasi yok, dogrudan cluster'in daemon'inda uretilir).
#   2. metrics-server addon'unu acar (HPA'nin CPU metrigi icin).
#   3. Demo altyapisini kurar: namespace + tek Postgres(10 db) + Kafka +
#      Redis + Keycloak (realm ConfigMap'i docker/keycloak'tan uretilir).
#   4. Uygulama chart'ini values-minikube.yaml ile kurar/gunceller.
#
# Docker Desktop K8s kullaniyorsan: 1. adimdaki docker-env satiri gereksiz
# (imajlar zaten ayni daemon'da) — SKIP_DOCKER_ENV=true ile atla;
# metrics-server'i da elle kur (addons minikube'e ozgu).
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> [1/4] Imajlar cluster icine build ediliyor (telco-crm/*:local)"
if [[ "${SKIP_DOCKER_ENV:-false}" != "true" ]]; then
  eval "$(minikube docker-env --shell bash)"
fi
scripts/build-images.sh   # REGISTRY=telco-crm TAG=local (script default'lari)

echo "==> [2/4] metrics-server addon (HPA icin)"
if [[ "${SKIP_DOCKER_ENV:-false}" != "true" ]]; then
  minikube addons enable metrics-server
fi

echo "==> [3/4] Demo altyapisi (namespace, postgres, kafka, redis, keycloak)"
kubectl apply -f deploy/k8s/demo-infra/namespace.yaml
# Realm dosyasinin tek dogruluk kaynagi docker/keycloak/ — ConfigMap oradan
# uretilir (dry-run+apply = idempotent; dosya degistiyse gunceller).
kubectl -n telco-crm create configmap keycloak-realm \
  --from-file=docker/keycloak/telco-crm-realm.json \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f deploy/k8s/demo-infra/

echo "==> [4/4] Uygulama chart'i (14 servis)"
helm upgrade --install telco-crm deploy/helm/telco-crm \
  -n telco-crm -f deploy/helm/telco-crm/values-minikube.yaml

cat <<'EOF'

Kurulum tamam. Izlemek icin:
  kubectl get pods -n telco-crm -w
Ilk acilis taze makinede 20-25 dk surebilir (once config-server/eureka, digerleri startup
probe toleransiyla arkadan gelir; birkac restart NORMALDIR).

Gateway'e erisim:
  kubectl port-forward -n telco-crm svc/gateway-server 8888:8888
HPA durumu:
  kubectl get hpa -n telco-crm
Temizlik:
  scripts/k8s-demo-down.sh
EOF
