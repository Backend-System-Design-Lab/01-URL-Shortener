# 04. Experiment

## 1. 실험 목적

MySQL만 사용하는 초기 리다이렉트 구조에서 VU 증가에 따라 처리량과 응답 시간이 어떻게 변하는지 확인한다.

측정 결과는 이후 Redis 캐시 적용 전후를 비교하기 위한 Baseline으로 사용한다.

## 2. 가설

> 모든 리다이렉트 요청이 MySQL 조회를 수행하므로 VU가 증가하면 응답 시간이 증가할 것이다.

> Redis 캐시를 적용하면 반복적인 DB 조회가 줄어들어 p95와 p99가 감소하고 처리량이 증가할 것이다.

## 3. 비교 대상

| 구분 | Baseline | Experiment |
|---|---|---|
| 구조 | Spring Boot → MySQL | Spring Boot → Redis → MySQL |
| 조회 방식 | 요청마다 DB 조회 | Cache Aside |
| Redirect | 302 | 302 |
| 테스트 VU | 20, 50, 100 | 20, 50, 100 |
| 실행 시간 | 조건별 1분 | 조건별 1분 |

## 4. 통제 변수

- 동일한 단축 URL 1개 반복 조회
- 애플리케이션 인스턴스 1개
- MySQL 인스턴스 1개
- Java 25
- Platform Thread 사용
- HikariCP 기본 설정
- Redirect 추적 비활성화
- Think Time 없음
- 조건별 테스트 시간 1분

## 5. 실험 환경

| 항목 | 값 |
|---|---|
| 실행 환경 | macOS, Docker Desktop |
| Java | 25 |
| Spring Boot | 4.1.0 |
| Database | MySQL 8.4 |
| Redis |  Baseline 미적용 / Experiment 적용 |
| k6 | 2.1.0 |
| Prometheus | 3.13.0 |
| Grafana | 13.1.1 |
| CPU / Memory | 미기록 |

## 6. 부하 테스트 시나리오

### Smoke Test

- 목적: URL 생성과 리다이렉트 기능 확인
- VU: 1
- 실행 시간: 10초
- 조건:
    - Check 성공률 99% 초과
    - 실패율 1% 미만
    - p95 500ms 미만

### Load Test

- 목적: VU별 처리량과 응답 시간 측정
- VU: 20, 50, 100
- 실행 시간: 조건별 1분
- 요청 대상: 동일한 단축 URL
- Redirect 추적: 비활성화

### Stress Test

- 수행 여부: 미실행
- 목적: 오류 또는 급격한 지연이 발생하는 한계 구간 확인

## 7. 측정 지표

### k6

- 요청 수
- RPS
- 평균 응답 시간
- p95
- p99
- 최대 응답 시간
- 실패율
- Check 성공률

### 서버 관점

- CPU
- JVM Heap
- 활성 스레드
- HikariCP 활성·대기 연결
- MySQL 조회량

서버 지표는 이번 측정에서 별도로 기록하지 않았다.

### Redis 적용 후 추가 지표

- 캐시 Hit 수
- 캐시 Miss 수
- 캐시 Hit Ratio
- Redis 명령 처리량
- MySQL 조회 횟수 변화

## 8. Warm-up

- Baseline: 별도 Warm-up 미실행
- Redis: `setup()`에서 리다이렉트를 한 번 호출해 캐시 저장
- 측정 제외 구간: URL 생성 요청과 캐시 Warm-up 요청
- 실제 측정 요청: Redis Cache Hit 상태
- 한계: Baseline과 Redis의 Warm-up 조건이 완전히 동일하지 않아 최종 비교 시 재측정이 필요하다.

## 9. Baseline 결과

### DB Only Redirect

- 구조: k6 → Spring Boot → MySQL
- Redirect: 302
- 테스트 대상: 동일한 단축 URL 반복 조회
- 요청 수: `setup()` 요청을 제외한 `iterations`

| VU | 실행 시간 | 요청 수 | RPS | 평균 | p95 | p99 | 최대 | 실패율 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 1분 | 477,253 | 7,950.61 | 2.38ms | 4.35ms | 6.28ms | 52.12ms | 0.00% |
| 50 | 1분 | 443,039 | 7,379.92 | 6.57ms | 16.17ms | 27.73ms | 305.05ms | 0.00% |
| 100 | 1분 | 512,241 | 8,531.95 | 11.49ms | 32.96ms | 49.88ms | 288.32ms | 0.00% |

### 관찰

- 모든 조건에서 실패율은 0%, Check 성공률은 100%였다.
- VU가 증가할수록 평균, p95, p99 응답 시간이 증가했다.
- 20 VU 대비 100 VU에서 p95는 약 7.6배 증가했다.
- 100 VU의 처리량은 가장 높았지만 응답 시간 증가 폭도 컸다.
- 50 VU의 RPS가 상대적으로 낮아 단일 실행 결과의 변동성이 확인됐다.
- 서버와 DB 지표를 측정하지 않았으므로 현재 결과만으로 MySQL을 병목으로 단정할 수는 없다.

## 10. 개선 내용

리다이렉트 조회에 Redis Cache Aside 전략을 적용했다.

```text
Redis 조회
→ Cache Hit: 원본 URL 반환
→ Cache Miss: MySQL 조회
→ Redis 저장
→ 원본 URL 반환
```

기대 효과:

- 반복 DB 조회 감소
- p95와 p99 감소
- 처리량 증가
- HikariCP 사용량 감소

## 11. 개선 후 결과

### Redis Redirect

| VU | 실행 시간 | 요청 수 | RPS | 평균 | p95 | p99 | 최대 | 실패율 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 1분 | 789,780 | 13,155.12 | 1.39ms | 2.81ms | 5.67ms | 198.76ms | 0.00% |
| 50 | 1분 | 975,688 | 16,238.53 | 2.90ms | 6.10ms | 11.20ms | 192.33ms | 0.00% |
| 100 | 1분 | 1,043,207 | 17,371.33 | 5.49ms | 11.93ms | 21.52ms | 154.10ms | 0.00% |

### Baseline 비교

| VU | 지표 | Baseline | Redis 적용 | 변화 |
|---:|---|---:|---:|---:|
| 20 | RPS | 7,950.61 | 13,155.12 | 65.46% 증가 |
| 20 | p95 | 4.35ms | 2.81ms | 35.40% 감소 |
| 20 | p99 | 6.28ms | 5.67ms | 9.71% 감소 |
| 50 | RPS | 7,379.92 | 16,238.53 | 120.04% 증가 |
| 50 | p95 | 16.17ms | 6.10ms | 62.28% 감소 |
| 50 | p99 | 27.73ms | 11.20ms | 59.61% 감소 |
| 100 | RPS | 8,531.95 | 17,371.33 | 103.60% 증가 |
| 100 | p95 | 32.96ms | 11.93ms | 63.80% 감소 |
| 100 | p99 | 49.88ms | 21.52ms | 56.86% 감소 |

### 캐시 동작 확인

100 VU 부하 테스트 후 Micrometer 지표를 확인했다.

| 지표 | 결과 |
|---|---:|
| Cache Hit | 955,178 |
| Cache Miss | 1 |
| Cache Hit Ratio | 약 99.9999% |
| DB Lookup | 1 |

`setup()`에서 수행한 첫 조회는 Cache Miss로 MySQL을 조회했고,
이후 반복 요청은 Redis Cache Hit로 처리됐다.

따라서 부하 테스트 구간에서는 대부분의 요청이 MySQL 조회 없이 처리된 것을 확인했다.

## 12. 결과 분석

Redis 적용 후 모든 VU 구간에서 실패율 0%와 Check 성공률 100%를 유지했다.

20 VU에서는 RPS가 약 65% 증가했고 p95는 약 35% 감소했다. 50 VU와 100 VU에서는 RPS가 두 배 이상으로 증가했으며 p95와 p99도 약 56~64% 감소했다.

따라서 반복 조회가 많은 환경에서는 Redis Cache Hit를 통해 MySQL 조회를 생략하는 방식이 처리량과 응답 지연 개선에 효과적이었다.

다만 이번 테스트는 고정 VU 방식이므로 응답이 빨라지면 같은 시간 동안 더 많은 요청을 보내게 된다. 또한 CPU, HikariCP, MySQL 조회량을 기록하지 않았으므로 DB 부하가 실제로 얼마나 감소했는지는 추가 측정이 필요하다.

## 13. Platform Thread와 Virtual Thread 비교

Redis 적용 실험 이후 동일한 조건으로 비교한다.

### 실험 A

```text
Java 25 + Platform Thread
VIRTUAL_THREADS_ENABLED=false
```

### 실험 B

```text
Java 25 + Virtual Thread
VIRTUAL_THREADS_ENABLED=true
```

| 지표 | Platform Thread | Virtual Thread |
|---|---:|---:|
| RPS | 미측정 | 미측정 |
| p95 | 미측정 | 미측정 |
| p99 | 미측정 | 미측정 |
| CPU | 미측정 | 미측정 |
| 활성 스레드 | 미측정 | 미측정 |
| DB Pool 대기 | 미측정 | 미측정 |
| 오류율 | 미측정 | 미측정 |

## 14. 실험 한계

- 로컬 Docker 환경에서 테스트했다.
- k6, 애플리케이션, MySQL이 같은 장비의 자원을 사용한다.
- 조건별 테스트를 한 번씩만 수행했다.
- 테스트 시간이 조건별 1분으로 짧다.
- CPU, JVM, HikariCP, MySQL 지표를 기록하지 않았다.
- 동일한 단축 URL 하나만 반복 조회했다.
- 별도의 Warm-up 조건을 적용하지 않았다.

## 15. 후속 실험

- [x] Redis Cache Aside 적용
- [x] 동일한 조건으로 Redis 적용 전후 비교
- [ ] Prometheus와 Grafana 서버 지표 기록
- [ ] 조건별 3회 측정 후 중앙값 비교
- [ ] Platform Thread와 Virtual Thread 비교
- [ ] 더 높은 VU로 Stress Test 수행