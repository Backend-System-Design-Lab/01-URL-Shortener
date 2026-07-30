#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

TEST_FILE="${1:-stress-test.js}"

docker compose up -d --wait app prometheus grafana

echo "Stress Test를 시작합니다."
echo "시스템 부하가 지나치게 높으면 Ctrl+C로 중단하세요."

docker compose --profile test run --rm \
  -e VUS="${VUS:-100}" \
  -e DURATION="${DURATION:-2m}" \
  k6 run "/scripts/${TEST_FILE}"