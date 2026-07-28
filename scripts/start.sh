#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f ".env" ]]; then
  echo ".env 파일이 없어 .env.example을 복사합니다."
  cp .env.example .env
fi

docker compose up --build -d

echo
echo "서비스 실행 상태:"
docker compose ps

echo
echo "접속 주소:"
echo "- Application: http://localhost:${APP_PORT:-8080}"
echo "- Prometheus: http://localhost:${PROMETHEUS_PORT:-9090}"
echo "- Grafana:    http://localhost:${GRAFANA_PORT:-3000}"