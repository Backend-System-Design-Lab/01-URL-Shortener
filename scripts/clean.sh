#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--volumes" ]]; then
  echo "컨테이너, 네트워크, Prometheus/Grafana 데이터를 모두 삭제합니다."
  docker compose down -v --remove-orphans
else
  echo "컨테이너와 네트워크를 삭제합니다."
  echo "Named Volume은 유지합니다."
  docker compose down --remove-orphans
fi

rm -rf build