#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.multi.yml"

BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:-100}"
DURATION="${DURATION:-120s}"

RESULT_DIR="${ROOT_DIR}/results/multi-instance/failover"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="${RESULT_DIR}/${RUN_ID}"

mkdir -p "${RUN_DIR}"

cleanup() {
  echo
  echo "[cleanup] Ensuring app1 is running..."

  docker compose \
    -f "${COMPOSE_FILE}" \
    start app1 >/dev/null 2>&1 || true
}

trap cleanup EXIT

sleep_until() {
  local target="$1"

  while (( SECONDS < target )); do
    sleep 1
  done
}

wait_for_app1() {
  echo "Waiting for app1 health..."

  for _ in {1..60}; do
    container_id="$(
      docker compose \
        -f "${COMPOSE_FILE}" \
        ps -q app1
    )"

    if [[ -n "${container_id}" ]]; then
      health="$(
        docker inspect \
          --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
          "${container_id}" 2>/dev/null || true
      )"

      if [[ "${health}" == "healthy" ]]; then
        echo "app1 is healthy."
        return 0
      fi
    fi

    sleep 1
  done

  echo "ERROR: app1 did not become healthy."
  return 1
}

echo "========================================"
echo "Multi-instance failover test"
echo "========================================"
echo "VU       : ${VUS}"
echo "Duration : ${DURATION}"
echo "Result   : ${RUN_DIR}"
echo

echo "[1/7] Checking containers"

docker compose \
  -f "${COMPOSE_FILE}" \
  ps

echo
echo "[2/7] Creating test URL"

response="$(
  curl -fsS \
    -X POST \
    "${BASE_URL}/api/v1/data/shorten" \
    -H 'Content-Type: application/json' \
    -d "{
      \"longUrl\":
      \"https://example.com/failover/${RUN_ID}\"
    }"
)"

SHORT_CODE="$(echo "${response}" | jq -r '.shortCode')"

if [[ -z "${SHORT_CODE}" || "${SHORT_CODE}" == "null" ]]; then
  echo "ERROR: shortCode was not created."
  exit 1
fi

echo "shortCode=${SHORT_CODE}"

echo
echo "[3/7] Warming Redis cache"

status="$(
  curl -s \
    -o /dev/null \
    -w '%{http_code}' \
    "${BASE_URL}/api/v1/${SHORT_CODE}"
)"

if [[ "${status}" != "302" ]]; then
  echo "ERROR: warm-up request failed. status=${status}"
  exit 1
fi

echo "Warm-up succeeded."

cat > "${RUN_DIR}/timeline.txt" <<EOF
test_start=$(date -Iseconds)
short_code=${SHORT_CODE}
vus=${VUS}
duration=${DURATION}
EOF

echo
echo "[4/7] Starting k6"

SECONDS=0

BASE_URL="${BASE_URL}" \
SHORT_CODE="${SHORT_CODE}" \
VUS="${VUS}" \
DURATION="${DURATION}" \
k6 run \
  --summary-export "${RUN_DIR}/summary.json" \
  "${ROOT_DIR}/k6/multi-instance-failover-test.js" \
  > "${RUN_DIR}/k6.log" 2>&1 &

K6_PID=$!

echo
echo "===== Phase 1: both apps healthy ====="

sleep_until 30

echo "app1_stopped=$(date -Iseconds)" \
  >> "${RUN_DIR}/timeline.txt"

echo
echo "[5/7] Stopping app1"

docker compose \
  -f "${COMPOSE_FILE}" \
  stop app1

echo
echo "===== Phase 2: app1 down ====="

sleep_until 60

echo "app1_started=$(date -Iseconds)" \
  >> "${RUN_DIR}/timeline.txt"

echo
echo "[6/7] Starting app1"

docker compose \
  -f "${COMPOSE_FILE}" \
  start app1

wait_for_app1

echo "app1_healthy=$(date -Iseconds)" \
  >> "${RUN_DIR}/timeline.txt"

echo
echo "===== Phase 3: app1 recovered ====="

sleep_until 120

set +e
wait "${K6_PID}"
K6_STATUS=$?
set -e

echo "test_end=$(date -Iseconds)" \
  >> "${RUN_DIR}/timeline.txt"

docker compose \
  -f "${COMPOSE_FILE}" \
  logs --no-color nginx \
  --since=5m \
  > "${RUN_DIR}/nginx.log"

echo
echo "[7/7] Result"

cat "${RUN_DIR}/timeline.txt"

echo
jq '{
  requests: .metrics.http_reqs.count,
  rps: (.metrics.http_reqs.rate * 100 | round / 100),
  average_ms: (.metrics.http_req_duration.avg * 100 | round / 100),
  p95_ms: (.metrics.http_req_duration["p(95)"] * 100 | round / 100),
  max_ms: (.metrics.http_req_duration.max * 100 | round / 100),
  failure_rate_percent: (.metrics.http_req_failed.value * 100),
  check_success_rate_percent: (.metrics.checks.value * 100)
}' "${RUN_DIR}/summary.json"

echo
echo "Result directory:"
echo "${RUN_DIR}"

exit "${K6_STATUS}"