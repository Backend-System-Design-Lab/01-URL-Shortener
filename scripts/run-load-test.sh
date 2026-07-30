#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

TEST_FILE="${1:-load-test.js}"

docker compose up -d --wait app prometheus grafana

docker compose --profile test run --rm \
  -e VUS="${VUS:-20}" \
  -e DURATION="${DURATION:-1m}" \
  k6 run "/scripts/${TEST_FILE}"