# 05. Retrospective

## 1. 해결한 문제

처음에는 URL Shortener에서 단축 코드를 만드는 방법이 가장 중요한 문제라고 생각했다.

실제로 부하를 걸어보니 더 먼저 드러난 문제는 리다이렉트 조회였다. 모든 요청이 MySQL을 조회하면서 
HikariCP 10개가 모두 사용됐고 최대 약 85개의 요청이 커넥션을 기다렸따.

이후 프로젝트의 방향은 기능 추가보다 실제로 확인한 병목과 장애를 하나씩 줄이는 쪽으로 바뀌었다.

## 2. 최종 결과

| 항목 | 결과 |
|---|---|
| Redis 적용 | 100 VU RPS 8,531.95 → 17,371.33 |
| 응답 지연 | p95 32.96ms → 11.93ms |
| DB 조회 | 366,248회 → 1회 |
| Circuit Breaker | Redis 장애 구간 p95 약 200ms → 20~30ms |
| App Failover | 446,850건, 실패율 0% |
| Sentinel Failover | 938,870건, 실패율 0%, 8.567초 만에 Master 전환 |

성능 개선뿐 아니라 App과 Redis 장애가 발생했을 때 요청이 어떻게 이어지는지도 실제로 검증했다.

## 3. 주요 설계 판단

### 가장 잘한 결정

- 결정: 처음부터 Redis를 넣지 않고 DB Only Baseline을 먼저 측정했다.
- 이유: 어디가 느린지 확인하지 않은 상태에서 캐시를 넣으면 개선 근거를 설명하기 어렵다고 봤다.
- 결과: DB Lookup, HikariCP Active와 Pending을 통해 반복 조회와 커넥션 대기를 병목으로 확인한 뒤 Redis를 적용할 수 있었다.

### 다시 검토할 결정

- 결정: Sequence + Base62를 기본 전략으로 유지했다.
- 이유: 단일 MySQL에서는 가장 단순하고 충돌 확인이 필요 없다.
- 한계: INSERT 뒤 UPDATE가 필요하고 DB ID에 의존한다.
- 정리: 단일 인스턴스에서는 Sequence를 유지하고, 다중 App에서는 nodeId를 분리한 Snowflake를 검증했다.

## 4. 발생한 문제와 해결 과정

### 문제 1. Base62 코드 UNIQUE 충돌

MySQL 기본 Collation이 대소문자를 구분하지 않아 `a`와 `A`가 같은 값으로 처리됐다.

`short_code`를 `ascii_bin`으로 변경해 Base62 문자 체계와 DB Unique 규칙을 맞췄다.

### 문제 2. Hash 충돌 재시도 트랜잭션

UNIQUE 충돌 뒤 같은 트랜잭션에서 재시도하면 rollback-only 상태가 이어질 수 있었다.

`saveAndFlush()`로 충돌을 즉시 확인하고 저장 로직을 별도 Bean으로 분리해 `REQUIRES_NEW`로 재시도했다.

### 문제 3. Virtual Thread를 넣어도 빨라지지 않음

Virtual Thread는 Thread 수와 CPU를 줄였지만 RPS와 p95는 좋아지지 않았다.

HikariCP Active와 Pending을 같이 보니 두 방식 모두 커넥션 10개를 모두 사용하고 있었다. 병목은 Thread보다 DB Connection Pool이었다.

### 문제 4. Fallback만으로는 Redis 장애 지연이 남음

MySQL Fallback으로 실패율 0%는 유지했지만 모든 요청이 Redis 200ms Timeout을 기다렸다.

Circuit Breaker를 적용해 장애가 반복되면 Redis 호출을 건너뛰도록 했고 장애 구간 p95를 약 20~30ms 수준으로 낮췄다.

### 문제 5. 장애 우회와 자동 복구는 다른 문제

Circuit Breaker와 Fallback은 Redis 장애를 우회할 뿐 Redis 자체를 복구하지 않는다.

Master 1개, Replica 2개, Sentinel 3개를 구성했고 Master 중단 뒤 8.567초 만에 Replica가 승격되는 것을 확인했다.
Failover 동안 938,870건의 요청에서 실패는 없었다.

## 5. 트레이드오프

| 선택                 | 얻은 것                 | 추가된 부담                    |
|--------------------|----------------------|---------------------------|
| Redis Cache Aside  | DB 조회와 커넥션 대기 감소     | 캐시 정합성과 장애 처리             |
| Sequence + Base62  | 단순한 구현, 충돌 없음        | DB ID 의존, 추가 UPDATE       |
| Hash + Base62      | DB ID 비의존            | 충돌 확인과 재시도                |
| Snowflake + Base62 | 단일 INSERT, 다중 App 확장 | nodeId와 Clock Rollback 관리 |
| Circuit Breaker    | 반복 Timeout 감소        | 상태 전환과 복구 정책 관리           |
| Redis Sentinel     | Master 자동 Failover   | Replica, Sentinel 운영 복잡도  |

## 6. 운영 환경에서 추가할 사항

* [x] 다중 App 구조와 Nginx Failover 검증
* [x] Redis Sentinel Failover 검증
* [ ] Nginx 이중화 또는 Managed Load Balancer
* [ ] MySQL Replica와 자동 장애 조치
* [ ] 중앙 집중식 로그
* [ ] Alerting
* [ ] Secret Manager
* [ ] 백업과 복구 절차
* [ ] 장애 대응 Runbook

로컬 Docker에서 검증한 Failover는 독립 Failure Domain을 구성한 운영 환경과는 차이가 있다.

## 7. 기술적으로 배운 점

* 시스템 설계: 예상 병목보다 측정된 병목을 기준으로 다음 구조를 결정하는 편이 설명하기도 쉽고 결과도 명확했다.
* Spring: 트랜잭션 재시도는 예외 시점과 프록시 경계를 함께 봐야 했다.
* 데이터베이스: Unique Constraint는 값뿐 아니라 Collation 규칙에도 영향을 받는다.
* 캐시: Cache Aside를 넣으면 성능뿐 아니라 장애 시 Fallback과 복구 경로까지 같이 설계해야 한다.
* 동시성: Virtual Thread를 사용해도 DB Connection 같은 하위 자원이 제한되면 처리량이 바로 늘지 않는다.
* 장애 대응: Fallback과 Circuit Breaker는 요청을 보호하고 Sentinel은 Redis 계층을 복구한다.
* 모니터링: RPS 하나보다 p95, HikariCP Pending, DB Lookup, Cache Error를 같이 볼 때 원인을 찾기 쉬었다.

## 8. 면접 기반 정리

### 30초 요약

MySQL 기반 URL Shortener를 만들고 100 VU 부하에서 HikariCP 10개가 모두 사용되고 약 85개의 요청이 대기하는 것을 확인했습니다.
Redis Cache Aside를 적용해 DB 조회를 366,248회에서 1회로 줄였고 RPS는 8,531에서 17,371로 약 2배 증가했으며 p95는 32.96ms에서 11.93ms로 줄었습니다.
이후 Redis 장애에는 MySQL Fallback과 Circuit Breaker를 적용했고, Sentinel을 구성해 Master 장애 시 8.567초 만에 Replica가 승격되는 동안 938,870건의 요청을 실패 없이 처리했습니다.

### 핵심 질문

1. 왜 Redis를 적용했는가?

    * 100 VU에서 HikariCP Active가 10에 도달하고 Pending이 약 85까지 증가했다. 반복 DB 조회를 줄이는 것이 먼저라고 판단했다.

2. 왜 Virtual Thread로 해결하지 않았는가?

    * Thread 수는 줄었지만 HikariCP 10개가 그대로 병목이었다. 실행 모델보다 DB Connection 수가 먼저 제한 요소였다.

3. Sequence와 Snowflake 중 무엇을 선택했는가?

    * 단일 MySQL에서는 Sequence가 단순하다. 다중 App에서는 DB ID에 의존하지 않는 Snowflake를 사용하고 nodeId를 분리했다.

4. Redis가 죽으면 어떻게 되는가?

   * 첫 실패는 MySQL로 Fallback하고, 장애가 반복되면 Circuit Breaker가 Redis 호출을 차단한다. Sentinel은 Replica를 새 Master로 승격해 Redis 경로를 복구한다.

5. 현재 남은 SPOF는 무엇인가?

    * Nginx와 MySQL이다. Redis Sentinel도 같은 Docker 호스트에서 실행했기 때문에 호스트 장애까지 해결한 것은 아니다.

6. 가장 큰 트레이드오프는 무엇인가?

    * Redis로 DB 부하는 줄였지만 Fallback, Circuit Breaker, Sentinel까지 장애 대응 구조가 추가돼 운영 복잡도가 커졌다.

### 답변에 사용할 수치

* Redis 적용: RPS 8,531.95 → 17,371.33
* Redis 적용: p95 32.96ms → 11.93ms
* DB Lookup: 366,248 → 1
* 생성 전략 RPS: Sequence 1,624.60 / Hash 1298.60 / Snowflake 1,660.20
* App Failover: 446,850건, 실패율 0%
* Circuit Breaker: 장애 구간 p95 약 200ms → 약 20~30ms
* Sentinel Failover: 8.567초, 938,870건, 실패율 0%

## 9. 다음 프로젝트에 반영할 점

* 유지: 요구사항 정의 → Baseline 측정 → 병목 확인 → 개선 → 장애 주입 → 재측정
* 개선: 조건별 1회가 아니라 3회 이상 실행하고 중앙값을 비교한다.
* 개선: 실험을 시작하기 전에 결과 파일과 Grafana 캡처 위치를 먼저 정한다.
* 다음 실험: 한 호스트가 아닌 독립 Failure Domain, DB Failover, 메시지 큐 기반 비동기 처리
