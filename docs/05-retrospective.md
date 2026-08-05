# 05. Retrospective

## 1. 해결한 문제

읽기 요청이 많은 URL Shortener에서 모든 리다이렉트 요청이 MySQL을 조회하면서 발생하는 DB 부하와 커넥션 대기 문제를 개선했다.

MySQL 단독 구조를 Baseline으로 두고 Redis Cache Aside를 적용했으며, Platform Thread와 Virtual Thread, HikariCP 크기, 단축 코드 생성 전략을 동일한 조건에서 비교했다.

## 2. 최종 결과

100 VU, Platform Thread, HikariCP 최대 커넥션 10개 조건의 리다이렉트 실험 결과다.

| 항목 | 결과 |
|---|---|
| 주요 기능 | URL 생성, 302 리다이렉트, Redis Cache Aside, 생성 전략 전환 |
| 목표 RPS | DB Only Baseline 8,531.95 초과 |
| 측정 RPS | 17,371.33 |
| 목표 p95 | DB Only Baseline 32.96ms 미만 |
| 측정 p95 | 11.93ms |
| 오류율 | 0% |

Redis 적용 후 RPS는 약 103.6% 증가했고, p95는 약 63.8% 감소했다.

Stress Test에서는 100 VU부터 500 VU까지 오류율 0%를 유지했으며, Redis 구조는 전체 평균 약 17,598 RPS를 기록했다.

## 3. 주요 설계 판단

### 가장 잘한 결정

- 결정: 리다이렉트 조회에 Redis Cache Aside를 적용했다.
- 이유: 동일한 단축 URL이 반복 조회되는 읽기 중심 구조에서 매 요청마다 MySQL을 조회할 필요가 없다고 판단했다.
- 결과: 100 VU에서 DB 조회를 최초 1회로 줄였고, HikariCP 커넥션 대기를 제거해 RPS를 약 2배로 높였다.

### 다시 검토할 결정

- 결정: Sequence ID + Base62를 기본 생성 전략으로 유지했다.
- 문제: DB Auto Increment ID를 발급받은 후 `short_code`를 갱신하므로 INSERT와 UPDATE가 발생하며, 다중 인스턴스에서는 중앙 DB에 의존한다.
- 개선 방향: 단일 인스턴스에서는 단순한 Sequence 방식을 유지하되, 다중 인스턴스 전환 시 Snowflake + Base62 적용을 검토한다.

## 4. 발생한 문제와 해결 과정

### 문제 1. Base62 코드 UNIQUE 충돌

#### 현상

Sequence ID를 Base62로 변환했음에도 부하 테스트 중 `Duplicate entry` 오류가 발생했다.

#### 원인

Base62는 대소문자를 다른 문자로 사용하지만, MySQL의 기본 Collation은 대소문자를 구분하지 않았다.

따라서 `a`와 `A`, `gf`와 `GF`처럼 서로 다른 Base62 코드가 UNIQUE 인덱스에서 같은 값으로 처리됐다.

#### 해결

`short_code` 컬럼의 문자 집합과 Collation을 다음과 같이 변경했다.

```sql
CHARACTER SET ascii
COLLATE ascii_bin
````

#### 검증

Sequence 생성 테스트에서 97,495건을 처리하는 동안 UNIQUE 충돌과 요청 실패가 발생하지 않았다.

### 문제 2. Hash 충돌 재시도 트랜잭션

#### 현상

Hash 코드 저장 중 UNIQUE 충돌이 발생한 뒤 다음 재시도를 수행하더라도 트랜잭션이 정상적으로 처리되지 않을 수 있었다.

#### 원인

DB 제약조건 오류가 발생한 트랜잭션은 rollback-only 상태가 될 수 있으며, 같은 트랜잭션 안에서 다음 저장을 시도하면 재시도가 독립적으로 처리되지 않는다.

또한 `save()`만 사용하면 SQL 실행과 예외 발생이 트랜잭션 종료 시점까지 지연될 수 있었다.

#### 해결

`saveAndFlush()`로 DB 오류를 저장 시점에 확인하고, 저장 로직을 `ShortUrlWriter`로 분리했다.

각 저장 시도에는 `REQUIRES_NEW`를 적용해 이전 충돌과 다음 재시도가 서로 다른 트랜잭션에서 실행되도록 했다.

#### 검증

Hash 전략으로 77,933건을 생성했으며 오류율 0%를 기록했다.

### 문제 3. Virtual Thread 적용 후 성능 감소

#### 현상

Virtual Thread를 적용했지만 Platform Thread보다 RPS가 낮아지고 p95와 p99가 증가했다.

#### 원인

두 방식 모두 HikariCP 최대 커넥션 10개를 모두 사용했고, 약 80개 이상의 요청이 DB 커넥션을 기다렸다.

병목은 스레드 생성 비용보다 DB 커넥션 풀이었다.

#### 해결

Virtual Thread 적용 여부뿐 아니라 HikariCP Active와 Pending 지표를 함께 확인하고, 커넥션 풀 크기를 5, 10, 20으로 나누어 추가 실험했다.

#### 검증

이번 환경에서는 Pool 10이 처리량과 응답시간 측면에서 가장 균형적인 결과를 보였다.

## 5. 트레이드오프

| 선택                 | 얻은 것                       | 포기한 것                          |
| ------------------ | -------------------------- | ------------------------------ |
| Redis Cache Aside  | DB 조회와 커넥션 대기 감소           | 캐시 정합성과 장애 대응 복잡도 증가           |
| Sequence + Base62  | 단순한 구현과 충돌 없는 코드           | DB ID 의존, ID 추정 가능성, 추가 UPDATE |
| Hash + Base62      | 고정 길이 코드와 DB ID 비의존성       | 충돌 조회와 재시도 비용                  |
| Snowflake + Base62 | 사전 조회 없는 단일 INSERT와 분산 확장성 | nodeId 관리와 시스템 시간 역행 처리 필요     |

## 6. 운영 환경에서 추가할 사항

* [ ] 다중 인스턴스
* [ ] Load Balancer
* [ ] Database 이중화
* [ ] Cache 고가용성
* [ ] 중앙 집중식 로그
* [ ] Alerting
* [ ] Secret Manager
* [ ] 백업 및 복구
* [ ] 장애 대응 Runbook

## 7. 기술적으로 배운 점

* 시스템 설계: 성능 결과만 보지 않고 애플리케이션, 커넥션 풀, DB 지표를 함께 확인해야 병목을 판단할 수 있다.
* Spring: 트랜잭션 전파와 프록시 경계를 고려해 재시도 로직을 별도 Bean으로 분리해야 한다.
* 데이터베이스: UNIQUE 제약조건은 값뿐 아니라 컬럼의 Collation 규칙에도 영향을 받는다.
* 캐시: Cache Aside는 읽기 성능에 효과적이지만 캐시 장애와 정합성 문제를 함께 고려해야 한다.
* 성능 테스트: 비교 조건, 워밍업, 데이터 초기화와 반복 측정 여부가 결과의 신뢰성에 영향을 준다.
* 모니터링: RPS와 응답시간뿐 아니라 HikariCP Active·Pending, DB Lookup, Cache Hit Ratio를 함께 확인해야 한다.

## 8. 면접 기반 정리

### 30초 요약

MySQL 기반 URL Shortener를 구현하고 리다이렉트 부하를 측정했습니다. 100 VU에서 HikariCP 커넥션 10개가 모두 사용되고 최대 약 85개의 요청이 대기하는 것을 확인해 Redis Cache Aside를 적용했습니다. 그 결과 DB 조회를 최초 1회로 줄였고, RPS는 8,531에서 17,371로 약 2배 증가했으며 p95는 32.96ms에서 11.93ms로 감소했습니다. 또한 Sequence, Hash, Snowflake 생성 전략을 비교해 현재 단일 인스턴스에서는 단순한 Sequence를 유지하고, 분산 확장 시 Snowflake를 적용할 수 있도록 설계했습니다.

### 핵심 질문

1. 왜 이 구조를 선택했는가?

    * 읽기 비중이 높고 동일 URL이 반복 조회되므로 MySQL을 원본 저장소로 유지하면서 Redis Cache Aside로 조회 부하를 줄였다.

2. 다른 대안은 무엇이었는가?

    * DB Only, 로컬 캐시, Redis Write Through를 검토할 수 있다. 로컬 캐시는 다중 인스턴스 정합성 문제가 있고, Write Through는 생성 경로가 복잡해 현재 요구사항에는 Cache Aside가 적합하다고 판단했다.

3. 성능 개선을 어떻게 검증했는가?

    * 캐시 활성화 여부만 변경하고 VU, 실행 시간, Thread, HikariCP 크기를 동일하게 통제해 k6와 Prometheus·Grafana로 비교했다.

4. 현재 구조의 SPOF는 무엇인가?

    * 단일 애플리케이션, MySQL, Redis 인스턴스가 각각 SPOF다.

5. 운영 환경에서는 어떻게 확장할 것인가?

    * Load Balancer와 다중 애플리케이션 인스턴스를 구성하고, Snowflake nodeId를 분리한다. Redis 고가용성과 MySQL 복제 및 샤딩도 트래픽 규모에 따라 적용한다.

6. 가장 큰 트레이드오프는 무엇인가?

    * Redis로 성능을 높이는 대신 캐시 정합성, 장애 대응, 운영 복잡도를 추가로 관리해야 한다는 점이다.

### 답변에 사용할 수치

* 데이터 규모: Redis Stress Test 리다이렉트 요청 약 459만 건
* RPS: 100 VU 기준 17,371.33
* p95: 11.93ms
* p99: 21.52ms
* 오류율: 0%
* 개선 전후 수치: RPS 8,531.95 → 17,371.33, p95 32.96ms → 11.93ms
* 생성 전략 RPS: Sequence 1,624.60 / Hash 1,298.60 / Snowflake 1,660.20

## 9. 다음 프로젝트에 반영할 점

* 유지할 방식: 요구사항 정의 → Baseline 측정 → 지표 분석 → 개선 → 동일 조건 재측정
* 변경할 방식: 조건별 한 번이 아니라 3회 이상 실행하고 중앙값을 비교한다.
* 새롭게 실험할 기술: 다중 인스턴스, Load Balancer, 메시지 큐 기반 비동기 통계 처리, 장애 복구 테스트
