#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REQUESTS="${REQUESTS:-2000}"
CONCURRENCY="${CONCURRENCY:-50}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULT_DIR="${ROOT_DIR}/results/multi-instance/create"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="${RESULT_DIR}/${RUN_ID}"

mkdir -p "${RUN_DIR}/responses"

echo "========================================"
echo "Multi-instance Snowflake create test"
echo "========================================"
echo "Base URL    : ${BASE_URL}"
echo "Requests    : ${REQUESTS}"
echo "Concurrency : ${CONCURRENCY}"
echo "Result dir  : ${RUN_DIR}"
echo

echo "[1/5] Nginx health check"

curl -fsS "${BASE_URL}/nginx-health" >/dev/null

echo "Nginx is healthy."
echo

echo "[2/5] Application environment"

docker compose -f "${ROOT_DIR}/docker-compose.multi.yml" \
  exec -T app1 sh -lc '
    echo "app1 strategy=$SHORT_CODE_STRATEGY nodeId=$SHORT_CODE_NODE_ID"
  '

docker compose -f "${ROOT_DIR}/docker-compose.multi.yml" \
  exec -T app2 sh -lc '
    echo "app2 strategy=$SHORT_CODE_STRATEGY nodeId=$SHORT_CODE_NODE_ID"
  '

echo
echo "[3/5] Sending concurrent create requests"

export BASE_URL
export RUN_DIR
export RUN_ID

seq 1 "${REQUESTS}" |
  xargs -P "${CONCURRENCY}" -I {} \
    sh -c '
      index="$1"

      curl -sS \
        -D "${RUN_DIR}/responses/${index}.headers" \
        -o "${RUN_DIR}/responses/${index}.json" \
        -w "%{http_code}" \
        -X POST "${BASE_URL}/api/v1/data/shorten" \
        -H "Content-Type: application/json" \
        -d "{
          \"longUrl\":
          \"https://example.com/multi-instance/${RUN_ID}/${index}\"
        }" \
        > "${RUN_DIR}/responses/${index}.status" \
        || echo "curl_error" \
        > "${RUN_DIR}/responses/${index}.status"
    ' _ {}

echo "Requests completed."
echo

echo "[4/5] Aggregating results"

find "${RUN_DIR}/responses" \
  -name '*.status' \
  -print0 |
  xargs -0 cat |
  sort |
  uniq -c |
  awk '{$1=$1; print}' \
  > "${RUN_DIR}/status-counts.txt"

find "${RUN_DIR}/responses" \
  -name '*.headers' \
  -print0 |
  xargs -0 grep -hi '^X-Upstream-Addr:' |
  sed 's/\r$//' |
  awk '{print $2}' |
  sort |
  uniq -c |
  awk '{$1=$1; print}' \
  > "${RUN_DIR}/upstream-counts.txt"

find "${RUN_DIR}/responses" \
  -name '*.json' \
  -print0 |
  xargs -0 -n1 jq -r \
    'if .shortCode then .shortCode else empty end' \
  > "${RUN_DIR}/short-codes.txt"

TOTAL_CODES="$(
  wc -l < "${RUN_DIR}/short-codes.txt" |
  tr -d ' '
)"

UNIQUE_CODES="$(
  sort -u "${RUN_DIR}/short-codes.txt" |
  wc -l |
  tr -d ' '
)"

DUPLICATE_CODES="$((TOTAL_CODES - UNIQUE_CODES))"

SUCCESS_COUNT="$(
  awk '
    $2 ~ /^20[0-9]$/ {
      total += $1
    }
    END {
      print total + 0
    }
  ' "${RUN_DIR}/status-counts.txt"
)"

FAILED_COUNT="$((REQUESTS - SUCCESS_COUNT))"

cat > "${RUN_DIR}/summary.txt" <<EOF
run_id=${RUN_ID}
requests=${REQUESTS}
concurrency=${CONCURRENCY}
success_count=${SUCCESS_COUNT}
failed_count=${FAILED_COUNT}
total_short_codes=${TOTAL_CODES}
unique_short_codes=${UNIQUE_CODES}
duplicate_short_codes=${DUPLICATE_CODES}
EOF

echo
echo "===== HTTP status ====="
cat "${RUN_DIR}/status-counts.txt"

echo
echo "===== Upstream distribution ====="
cat "${RUN_DIR}/upstream-counts.txt"

echo
echo "===== Summary ====="
cat "${RUN_DIR}/summary.txt"

echo
echo "[5/5] Validation"

if [[ "${FAILED_COUNT}" -ne 0 ]]; then
  echo "FAIL: Some create requests failed."
  exit 1
fi

if [[ "${TOTAL_CODES}" -ne "${REQUESTS}" ]]; then
  echo "FAIL: Some responses did not contain shortCode."
  exit 1
fi

if [[ "${DUPLICATE_CODES}" -ne 0 ]]; then
  echo "FAIL: Duplicate short codes were generated."
  exit 1
fi

UPSTREAM_COUNT="$(
  wc -l < "${RUN_DIR}/upstream-counts.txt" |
  tr -d ' '
)"

if [[ "${UPSTREAM_COUNT}" -lt 2 ]]; then
  echo "FAIL: Requests were not distributed to both instances."
  exit 1
fi

echo "PASS: Requests were distributed to both applications."
echo "PASS: All create requests succeeded."
echo "PASS: No duplicate short codes were generated."