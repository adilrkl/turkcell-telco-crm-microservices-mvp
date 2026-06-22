#!/usr/bin/env bash
# Tum Telco CRM Spring servislerini TAZE jar'larla, bagimlilik sirasinda baslatir.
# Onkosul: docker compose up -d (postgres/kafka/redis/keycloak + observability ayakta)
#          ./mvnw clean install -DskipTests (jar'lar guncel)
# Loglar: logs/<servis>.log   |   Durdurmak: scripts/stop-all.sh
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
LOGS="$ROOT/logs"; mkdir -p "$LOGS"
JAVA_OPTS="${JAVA_OPTS:--Xmx320m}"
VER="1.0.0-SNAPSHOT"

# Once altyapi (config + discovery), sonra is servisleri + edge.
INFRA=(
  "config-server:8889"
  "eureka-server:8761"
)
APPS=(
  "identity-service:8081"
  "customer-service:8082"
  "product-catalog-service:8083"
  "order-service:8084"
  "subscription-service:8085"
  "usage-service:8086"
  "billing-service:8087"
  "payment-service:8088"
  "notification-service:8089"
  "ticket-service:8090"
  "gateway-server:8888"
  "bff-server:9000"
)

kill_port() {
  local pid; pid=$(lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null)
  [ -n "$pid" ] && { kill $pid 2>/dev/null; sleep 1; }
}

start_one() {
  local name=$1 port=$2
  kill_port "$port"
  nohup java $JAVA_OPTS -jar "$name/target/$name-$VER.jar" > "$LOGS/$name.log" 2>&1 &
  echo "  baslatildi $name (:$port) pid $!"
}

wait_health() {
  local port=$1 name=$2 max=${3:-90}
  for ((i=0; i<max; i++)); do
    c=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null)
    [ "$c" = "200" ] && { echo "  UP   $name (:$port) ~$((i*2))s"; return 0; }
    sleep 2
  done
  echo "  FAIL $name (:$port) health 200 vermedi (logs/$name.log)"
  return 1
}

echo "== ALTYAPI (config -> discovery) =="
for e in "${INFRA[@]}"; do IFS=: read -r n p <<<"$e"; start_one "$n" "$p"; wait_health "$p" "$n" 60; done

echo "== IS SERVISLERI + EDGE (paralel start) =="
for e in "${APPS[@]}"; do IFS=: read -r n p <<<"$e"; start_one "$n" "$p"; sleep 1; done

echo "== Health bekleniyor =="
fail=0
for e in "${APPS[@]}"; do IFS=: read -r n p <<<"$e"; wait_health "$p" "$n" 120 || fail=1; done

echo ""
[ "$fail" = 0 ] && echo "TUM SERVISLER UP." || echo "BAZI SERVISLER UP DEGIL - ilgili logs/<servis>.log'a bak."
echo "Grafana: http://localhost:3000 | Prometheus: http://localhost:9090/targets"
