#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d app prometheus grafana

echo "Stress Test를 시작합니다."
echo "시스템 부하가 지나치게 높으면 Ctrl+C로 중단하세요."

docker compose --profile test run --rm k6 \
  run /scripts/stress-test.js