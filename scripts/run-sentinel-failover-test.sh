#!/usr/bin/env bash

set -euo pipefail

COMPOSE_FILE="docker-compose.sentinel.yml"

BASE_URL="${BASE_URL:-http://localhost:8080}"
VUS="${VUS:-100}"
DURATION="${DURATION:-120s}"

if [ -z "${SHORT_CODE:-}" ]; then
  echo "SHORT_CODE 환경변수가 필요합니다."
  echo "예: SHORT_CODE=abc123 ./scripts/run-sentinel-failover-test.sh"
  exit 1
fi

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
RESULT_DIR="results/sentinel/failover/${TIMESTAMP}"

mkdir -p "${RESULT_DIR}"

get_master() {
  docker compose -f "${COMPOSE_FILE}" \
    exec -T sentinel-1 \
    redis-cli -p 26379 --raw \
    SENTINEL get-master-addr-by-name url-shortener-master \
    | sed -n '1p' \
    | tr -d '\r'
}

now_ms() {
  python3 -c 'import time; print(int(time.time() * 1000))'
}

now_iso() {
  date '+%Y-%m-%dT%H:%M:%S%z'
}

FAILED_MASTER=""

cleanup() {
  if [ -n "${FAILED_MASTER}" ]; then
    docker compose -f "${COMPOSE_FILE}" \
      start "${FAILED_MASTER}" > /dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo "=== Sentinel Failover Test ==="
echo "SHORT_CODE=${SHORT_CODE}"
echo "VUS=${VUS}"
echo "DURATION=${DURATION}"
echo "RESULT_DIR=${RESULT_DIR}"
echo

echo "1. 애플리케이션 상태 확인"

curl -fsS "${BASE_URL}/actuator/health"
echo
echo

echo "2. Sentinel 합의 상태 확인"

for i in 1 2 3; do
  echo -n "sentinel-${i}: "

  docker compose -f "${COMPOSE_FILE}" \
    exec -T "sentinel-${i}" \
    redis-cli -p 26379 --raw \
    SENTINEL get-master-addr-by-name url-shortener-master \
    | head -n 1
done

echo

echo "3. Cache Warm-up"

for i in $(seq 1 10); do
  curl -s \
    -o /dev/null \
    -w "%{http_code}\n" \
    "${BASE_URL}/api/v1/${SHORT_CODE}"
done

echo

MASTER_BEFORE="$(get_master)"

if [ -z "${MASTER_BEFORE}" ]; then
  echo "현재 Redis Master를 확인할 수 없습니다."
  exit 1
fi

echo "현재 Master: ${MASTER_BEFORE}"

TEST_START="$(now_iso)"
TEST_START_MS="$(now_ms)"

echo
echo "4. k6 시작"

k6 run \
  --summary-export="${RESULT_DIR}/summary.json" \
  -e BASE_URL="${BASE_URL}" \
  -e SHORT_CODE="${SHORT_CODE}" \
  -e VUS="${VUS}" \
  -e DURATION="${DURATION}" \
  k6/sentinel-failover-test.js \
  | tee "${RESULT_DIR}/k6.log" &

K6_PID=$!

#
# 0 ~ 30초 : 정상
#

sleep 30

FAILED_MASTER="$(get_master)"

MASTER_STOP_TIME="$(now_iso)"
MASTER_STOP_MS="$(now_ms)"

echo
echo "======================================"
echo "Redis Master 장애 발생"
echo "Master: ${FAILED_MASTER}"
echo "Time: ${MASTER_STOP_TIME}"
echo "======================================"

docker compose -f "${COMPOSE_FILE}" \
  stop "${FAILED_MASTER}"

#
# Sentinel이 새로운 Master를 선택하는 시간 측정
#

(
  DEADLINE=$(( $(now_ms) + 30000 ))

  while true; do
    CURRENT_MS="$(now_ms)"

    if [ "${CURRENT_MS}" -gt "${DEADLINE}" ]; then
      echo "failover_timeout" \
        > "${RESULT_DIR}/master-after.txt"
      break
    fi

    NEW_MASTER="$(get_master 2>/dev/null || true)"

    if [ -n "${NEW_MASTER}" ] \
       && [ "${NEW_MASTER}" != "${FAILED_MASTER}" ]; then

      SWITCH_TIME="$(now_iso)"
      SWITCH_MS="$(now_ms)"

      echo "${NEW_MASTER}" \
        > "${RESULT_DIR}/master-after.txt"

      echo "${SWITCH_TIME}" \
        > "${RESULT_DIR}/master-switch-time.txt"

      echo "${SWITCH_MS}" \
        > "${RESULT_DIR}/master-switch-ms.txt"

      echo
      echo "======================================"
      echo "Sentinel Failover 완료"
      echo "New Master: ${NEW_MASTER}"
      echo "Time: ${SWITCH_TIME}"
      echo "======================================"

      break
    fi

    sleep 0.2
  done
) &

WATCH_PID=$!

#
# 30 ~ 60초 : 기존 Master DOWN
#

sleep 30

MASTER_RESTART_TIME="$(now_iso)"

echo
echo "======================================"
echo "기존 Master 재시작"
echo "Node: ${FAILED_MASTER}"
echo "Time: ${MASTER_RESTART_TIME}"
echo "======================================"

docker compose -f "${COMPOSE_FILE}" \
  start "${FAILED_MASTER}"

FAILED_MASTER=""

#
# 60 ~ 120초 : 복구 안정화
#

set +e
wait "${K6_PID}"
K6_EXIT_CODE=$?
set -e

wait "${WATCH_PID}" || true

TEST_END="$(now_iso)"

MASTER_AFTER="$(get_master)"

SWITCH_MS=""

if [ -f "${RESULT_DIR}/master-switch-ms.txt" ]; then
  SWITCH_MS="$(cat "${RESULT_DIR}/master-switch-ms.txt")"
fi

FAILOVER_MS=""

if [ -n "${SWITCH_MS}" ]; then
  FAILOVER_MS=$(( SWITCH_MS - MASTER_STOP_MS ))
fi

{
  echo "test_start=${TEST_START}"
  echo "short_code=${SHORT_CODE}"
  echo "vus=${VUS}"
  echo "duration=${DURATION}"

  echo "master_before=${MASTER_BEFORE}"
  echo "master_stopped=${MASTER_STOP_TIME}"

  echo "master_after=${MASTER_AFTER}"

  if [ -n "${FAILOVER_MS}" ]; then
    echo "sentinel_failover_ms=${FAILOVER_MS}"
  fi

  echo "old_master_restarted=${MASTER_RESTART_TIME}"
  echo "test_end=${TEST_END}"
  echo "k6_exit_code=${K6_EXIT_CODE}"

} > "${RESULT_DIR}/timeline.txt"

docker compose -f "${COMPOSE_FILE}" \
  logs sentinel-1 sentinel-2 sentinel-3 \
  > "${RESULT_DIR}/sentinel.log"

echo
echo "======================================"
echo "Test Complete"
echo "======================================"

cat "${RESULT_DIR}/timeline.txt"

echo
echo "Result:"
echo "${RESULT_DIR}"