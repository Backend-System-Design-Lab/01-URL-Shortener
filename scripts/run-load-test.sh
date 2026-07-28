#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d app prometheus grafana

docker compose --profile test run --rm k6 \
  run /scripts/load-test.js