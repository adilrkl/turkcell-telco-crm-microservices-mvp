#!/usr/bin/env bash
# Lokal K8s demosunu temizler: namespace silinince icindekiler (helm release
# kaydi dahil) birlikte gider. Imajlar minikube icinde kalir (tekrar kurulum
# hizli olsun diye); onlari da atmak istersen: minikube delete.
set -euo pipefail
kubectl delete namespace telco-crm --ignore-not-found
echo "Tamam: telco-crm namespace'i silindi."
