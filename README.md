# URL Shortener

> Backend System Design Lab — Week 1

URL 단축기를 구현한 뒤 부하와 장애를 직접 만들어 보면서 구조를 단계적으로 개선한 프로젝트입니다.

## 프로젝트 소개

처음에는 Spring Boot와 MySQL만 사용했습니다.

100 VU에서 모든 리다이렉트 요청이 MySQL을 조회했고 HikariCP 10개가 모두 사용되면서 최대 약 85개의 요청이 커넥션을 기다렸습니다.

이후 Redis Cache Aside, Circuit Breaker, 다중 App, Redis Sentinel을 순서대로 적용하고 같은 방식으로 측정했습니다.

```text
DB Only
→ Redis Cache Aside
→ Redis Fallback
→ Circuit Breaker
→ Redis Sentinel

Single App
→ Nginx + App1/App2
→ Snowflake nodeId 분리
→ App Failover
```

## 핵심 결과

| 실험                | 결과 |
|-------------------|---|
| Redis Cache Aside | 100 VU에서 RPS 8,531.95 → 17,371.33 |
| 응답 지연             | p95 32.96ms → 11.93ms |
| DB 조회             | 366,248회 → 1회 |
| 생성 전략             | Snowflake 1,660.20 RPS, Sequence 1,624.60 RPS |
| App Failover      | 446,850건, 실패율 0% |
| Circuit Breaker | Redis 장애 구간 p95 약 200ms → 20~30ms |
| Redis Sentienl | 938,870건, 실패율 0%, Failover 8.567초 |

## 기술 스택

| 구분               | 기술                                                    |
|------------------|-------------------------------------------------------|
| Application      | Java 25, Spring Boot 4.1.0, Spring Data JPA           |
| Database         | MySQL 8.4                                             |
| Cache            | Redis 7.4, Redis Sentinel                             |
| Resilience       | Resilience4j Circuit Breaker, Nginx Failover          | 
| Monitoring       | Spring Boot Actuator, Micrometer, Prometheus, Grafana |
| Performance Test | k6                                                    |
| Infrastructure   | Docker, Docker Compose, Nginx                         |

## 아키텍처

```text
                       Client
                         │
                       Nginx
                    ┌────┴────┐
                  App1       App2
                    │          │
             ┌──────┴──────────┴──────┐
             │                         │
           MySQL                  Sentinel x3
     Source of Truth                   │
                                Current Redis Master
                                  ┌────┴────┐
                               Replica   Replica
                               
Prometheus → App Metrics → Grafana
```

App Failover와 Redis Sentinel Failover는 각각 별도 Docker 환경에서 검증했습니다.

## 주요 설계

### Redis Cache Aside

```text
Redis 조회
→ Cache Hit: 원본 URL 반환
→ Cache Miss: MySQL 조회
→ Redis 저장
```

MySQL은 Source of Truth로 유지합니다. Redis 요청이 실패하면 MySQL로 Fallback하고, 장애가 반복되면 Circuit Breaker가 Redis 호출을 차단합니다.

### 단축 코드 생성 전략

| 전략                 | 저장 흐름                 | 특징                      |
| ------------------ |-----------------------|-------------------------|
| Sequence + Base62  | INSERT → ID → UPDATE  | 단순하고 충돌 없음              |
| Hash + Base62      | 충돌 확인 → Hash → INSERT | 고정 길이, 재시도 필요           |
| Snowflake + Base62 | 분산 ID 생성 → INSERT     | DB ID 비의존, 다중 App 확장 가능 |

단일 MySQL에서는 Sequence를 기본으로 두고, 다중 App에서는 서로 다른 nodeId를 사용하는 Snowflake를 검증했습니다.

### 장애 대응

```text
Redis 요청 실패
→ MySQL Fallback
→ 반복 실패 시 Circuit Breaker OPEN

Redis Master 장애
→ Sentinel이 Replica 승격
→ Lettuce가 새 Master 탐색
→ Cache 경로 복구

App 장애
→ Nginx가 다른 App으로 GET 요청 전환
```

## 실행 방법

### 사전 요구사항

* Java 25
* Docker / Docker Compose
* k6

### 환경 실행

```text
# 기본
docker compose up -d --build

# App 2개 + Nginx
docker compose -f docker-compose.multi.yml up -d --build

# Redis Sentinel
docker compose -f docker-compose.sentinel.yml up -d --build
```

상태 확인:
```curl http://localhost:8080/actuator/health```

### API

```text
curl -X POST \
  http://localhost:8080/api/v1/data/shorten \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com"}'

curl -i http://localhost:8080/api/v1/{shortCode}
```

### 생성 전략 변경
```text
SHORT_CODE_STRATEGY=sequence docker compose up -d --build app
SHORT_CODE_STRATEGY=hash docker compose up -d --build app
SHORT_CODE_STRATEGY=distributed SHORT_CODE_NODE_ID=1 \
  docker compose up -d --build app
```

### 테스트
```text
./gradlew clean test

VUS=20 DURATION=1m BASE_URL=http://localhost:8080 \
  k6 run k6/load-test.js
```
상세 실험 스크립트와 결과는 `k6/`, `scripts/`, `docs/04-experiment.md`에서 확인할 수 있습니다.

## 문서
| 문서                                         | 내용 |
|--------------------------------------------|---|
| [Requirements](docs/01-requirements.md)    | 범위와 성공 기준 |
| [Capacity](docs/02-capacity-estimation.md) | 트래픽, 저장량, 예상 병목 |
| [Architecture](docs/03-architecture.md)    | 최종 구조와 설계 판단 |
| [Experiment](docs/04-experiment.md)        | 부하, 병목, 장애 실험 결과 |
| [Retrospective](docs/05-retrospective.md)  | 문제 해결 과정과 면접용 정리 |

## 후속 과제
- 조건별 3회 이상 반복 측정 후 중앙값 비교
- Nginx와 MySQL 고가용성 구성
- 독립 Failure Domain에서 Redis Sentinel 재검증
- Redis 용량 또는 처리량 병목 발생 시 Cluster 기반 Sharding 검토
- 클릭 통계가 필요해질 경우 메시지 큐 기반 비동기 처리 검토