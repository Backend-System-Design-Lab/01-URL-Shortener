# URL Shortener

> Backend System Design Lab — Week 1

URL 단축기의 코드 생성, 조회, 캐시 및 확장 전략을 직접 구현하고
동일한 부하 조건에서 개선 전후 성능을 비교한다.

## 프로젝트 소개

URL 생성과 리다이렉트 기능을 구현하고,
읽기 중심 서비스에서 발생하는 데이터베이스 조회 및 커넥션 풀 병목을
부하 테스트와 서버 지표를 통해 분석한 프로젝트입니다.

MySQL 단독 구조를 Baseline으로 구성한 뒤 Redis Cache Aside를 적용했으며,
Platform·Virtual Thread, HikariCP 크기,
Sequence·Hash·Snowflake 단축 코드 생성 전략을 비교했습니다.

## 핵심 결과

| 실험 | 결과 |
|---|---|
| Redis Cache Aside | 100 VU에서 RPS 8,531.95 → 17,371.33 |
| 응답 지연 | p95 32.96ms → 11.93ms |
| DB 조회 | 100 VU 서버 지표 측정에서 366,248회 → 1회 |
| Stress Test | 500 VU까지 요청 실패율 0% |
| 생성 전략 | Snowflake 1,660.20 RPS, Sequence 1,624.60 RPS |

## 기술 스택

| 구분 | 기술 |
|---|---|
| Application | Java 25, Spring Boot 4.1.0, Spring Data JPA |
| Database | MySQL 8.4 |
| Cache | Redis 7.4 |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus, Grafana |
| Performance Test | k6 |
| Infrastructure | Docker, Docker Compose |

## 아키텍처

```text
Client
  │
  ▼
Spring Boot
  │
  ├── Redis Cache
  │      └── Cache Hit: 원본 URL 반환
  │
  └── MySQL
         └── Cache Miss 및 원본 데이터 저장
```

## 주요 설계

### Redis Cache Aside

```text
Redis 조회
→ Cache Hit: 원본 URL 반환
→ Cache Miss: MySQL 조회
→ Redis 저장
→ 원본 URL 반환
```

### 단축 코드 생성 전략

| 전략                 | 저장 흐름                   | 특징                       |
| ------------------ | ----------------------- | ------------------------ |
| Sequence + Base62  | INSERT → ID 발급 → UPDATE | 단순하고 충돌 없음               |
| Hash + Base62      | 중복 조회 → Hash → INSERT   | 고정 길이지만 충돌 처리 필요         |
| Snowflake + Base62 | 분산 ID 생성 → INSERT       | DB ID 비의존, 다중 인스턴스 확장 가능 |

현재 단일 인스턴스에서는 운영이 단순한 Sequence 방식을 기본값으로 유지하고,
다중 인스턴스 환경에서는 Snowflake 방식을 적용할 수 있도록 전략을 분리했습니다.


## 성능 실험

### Redis 적용 전후

| VU | 지표 | DB Only | Redis |
|---:|---|---:|---:|
| 100 | RPS | 8,531.95 | 17,371.33 |
| 100 | p95 | 32.96ms | 11.93ms |
| 100 | p99 | 49.88ms | 21.52ms |
| 100 | 실패율 | 0% | 0% |

### 생성 전략 비교

| 전략 | RPS | 평균 | p95 | 실패율 |
|---|---:|---:|---:|---:|
| Sequence | 1,624.60 | 12.12ms | 22.23ms | 0% |
| Hash | 1,298.60 | 15.18ms | 30.14ms | 0% |
| Snowflake | 1,660.20 | 11.85ms | 21.52ms | 0% |

상세한 실험 조건과 서버 지표는
[`docs/04-experiment.md`](docs/04-experiment.md)에 기록했습니다.

## 실행 방법

### 사전 요구사항

- Java 25
- Docker
- Docker Compose
- k6

### 전체 환경 실행

```bash
docker compose up -d --build
```

애플리케이션 상태 확인:

```bash
curl http://localhost:8080/actuator/health
```

### 단축 URL 생성

```bash
curl -X POST \
  http://localhost:8080/api/v1/data/shorten \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com"}'
```

### 리다이렉트 확인

```bash
curl -i \
  http://localhost:8080/api/v1/{shortCode}
```

### 생성 전략 변경

```bash
SHORT_CODE_STRATEGY=sequence docker compose up -d --build app
SHORT_CODE_STRATEGY=hash docker compose up -d --build app
SHORT_CODE_STRATEGY=distributed SHORT_CODE_NODE_ID=1 \
  docker compose up -d --build app
```

지원 전략:

- `sequence`: Auto Increment ID + Base62
- `hash`: SHA-256 Hash + Base62
- `distributed`: Snowflake ID + Base62

### 테스트 실행

```bash
./gradlew clean test
```

### 부하 테스트

리다이렉트 API 테스트:

```bash
VUS=20 \
DURATION=1m \
BASE_URL=http://localhost:8080 \
k6 run k6/load-test.js
```

생성 API 테스트:

```bash
RUN_ID="create-$(date +%s)" \
VUS=20 \
DURATION=1m \
BASE_URL=http://localhost:8080 \
k6 run k6/create-load.js
```

## 문서
| 문서 | 내용 |
|---|---|
| [Requirements](docs/01-requirements.md) | 기능·비기능 요구사항 |
| [Capacity](docs/02-capacity.md) | 트래픽 및 저장 용량 추정 |
| [Architecture](docs/03-architecture.md) | 구조와 주요 설계 판단 |
| [Experiment](docs/04-experiment.md) | 부하 테스트와 병목 분석 |
| [Retrospective](docs/05-retrospective.md) | 최종 결과와 회고 |

## 후속 과제
- 다중 애플리케이션 인스턴스와 Load Balancer 구성
- Snowflake nodeId 분리 검증
- Redis 고가용성 및 장애 시 MySQL Fallback 실험
- 조건별 반복 측정 후 중앙값 비교
- 조회 통계의 메시지 큐 기반 비동기 처리