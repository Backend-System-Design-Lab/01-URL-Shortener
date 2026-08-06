#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:-100}"
DURATION="${DURATION:-120s}"

RESULT_DIR="results/resilience"
RESULT_JSON="${RESULT_DIR}/redis-fallback-100vu.json"
RESULT_LOG="${RESULT_DIR}/redis-fallback-100vu.log"
TIMELINE_FILE="${RESULT_DIR}/redis-fallback-timeline.txt"

mkdir -p "$RESULT_DIR"

cleanup() {
    echo
    echo "[cleanup] Redis 실행 상태 복구"
    docker compose start redis >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

snapshot_metrics() {
    local phase="$1"

    curl -s "${BASE_URL}/actuator/prometheus" \
      | grep -E \
        'short_url_cache_(hit|miss|error|fallback)_total|short_url_db_lookup_total' \
      > "${RESULT_DIR}/metrics-${phase}.txt" || true
}

wait_for_redis() {
    until docker compose exec -T redis redis-cli ping 2>/dev/null \
      | grep -q PONG; do
        echo "[wait] Redis 준비 대기 중..."
        sleep 1
    done
}

wait_for_app() {
    until curl -sf "${BASE_URL}/actuator/health" \
      | grep -q '"status":"UP"'; do
        echo "[wait] 애플리케이션 준비 대기 중..."
        sleep 1
    done
}

sleep_until() {
    local target_second="$1"

    if (( SECONDS < target_second )); then
        sleep $((target_second - SECONDS))
    fi
}

echo "[1/7] Redis 실행 확인"
docker compose start redis >/dev/null
wait_for_redis

echo "[2/7] 애플리케이션 준비 확인"
wait_for_app

echo "[3/7] 테스트용 단축 URL 생성"

LONG_URL="https://example.com/redis-fallback-$(date +%s)"

RESPONSE=$(
    curl -sf -X POST \
      "${BASE_URL}/api/v1/data/shorten" \
      -H 'Content-Type: application/json' \
      -d "{\"longUrl\":\"${LONG_URL}\"}"
)

SHORT_CODE=$(echo "$RESPONSE" | jq -r '.shortCode // empty')

if [[ -z "$SHORT_CODE" ]]; then
    echo "[error] shortCode 생성 실패"
    echo "$RESPONSE"
    exit 1
fi

echo "shortCode=${SHORT_CODE}"

echo "[4/7] 캐시 Warm-up"

STATUS=$(
    curl -s \
      -o /dev/null \
      -w '%{http_code}' \
      "${BASE_URL}/api/v1/${SHORT_CODE}"
)

if [[ "$STATUS" != "302" ]]; then
    echo "[error] Warm-up 요청 실패: HTTP ${STATUS}"
    exit 1
fi

snapshot_metrics "before"

{
    echo "test_start=$(date -Iseconds)"
    echo "short_code=${SHORT_CODE}"
    echo "vus=${VUS}"
    echo "duration=${DURATION}"
} > "$TIMELINE_FILE"

echo "[5/7] k6 테스트 시작"

SECONDS=0

SHORT_CODE="$SHORT_CODE" \
BASE_URL="$BASE_URL" \
VUS="$VUS" \
DURATION="$DURATION" \
k6 run \
  --summary-export="$RESULT_JSON" \
  k6/redis-fallback-test.js \
  > "$RESULT_LOG" 2>&1 &

K6_PID=$!

sleep_until 30

snapshot_metrics "normal"

echo "[fault] Redis 중지: $(date -Iseconds)"
echo "redis_stopped=$(date -Iseconds)" >> "$TIMELINE_FILE"

docker compose stop redis

sleep_until 60

snapshot_metrics "outage"

echo "[recovery] Redis 시작: $(date -Iseconds)"
echo "redis_started=$(date -Iseconds)" >> "$TIMELINE_FILE"

docker compose start redis
wait_for_redis

echo "redis_ready=$(date -Iseconds)" >> "$TIMELINE_FILE"

sleep_until 90
snapshot_metrics "recovered-30s"

sleep_until 120
snapshot_metrics "recovered-60s"

set +e
wait "$K6_PID"
K6_STATUS=$?
set -e

echo "test_end=$(date -Iseconds)" >> "$TIMELINE_FILE"

echo "[6/7] 실험 종료"
cat "$RESULT_LOG"

echo
echo "[7/7] 결과 파일"
echo "- ${RESULT_JSON}"
echo "- ${RESULT_LOG}"
echo "- ${TIMELINE_FILE}"
echo "- ${RESULT_DIR}/metrics-before.txt"
echo "- ${RESULT_DIR}/metrics-normal.txt"
echo "- ${RESULT_DIR}/metrics-outage.txt"
echo "- ${RESULT_DIR}/metrics-recovered.txt"

exit "$K6_STATUS"