#!/usr/bin/env bash
# Host'ta calisan tum Telco CRM Spring servislerini durdurur (port bazli).
# Altyapi (Docker) container'larina dokunmaz.
set -u

PORTS=(8889 8761 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8888 9000)

echo "== Spring servisleri durduruluyor =="
for p in "${PORTS[@]}"; do
  pid=$(lsof -nP -iTCP:"$p" -sTCP:LISTEN -t 2>/dev/null)
  if [ -n "$pid" ]; then
    kill $pid 2>/dev/null && echo "  durduruldu :$p (pid $pid)"
  else
    echo "  :$p bos"
  fi
done
echo "Bitti. (Docker altyapisi icin: docker compose down)"
