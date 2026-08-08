# 01. Requirements

## 1. 문제 정의

긴 URL을 짧은 코드로 바꾸고, 단축 URL 요청을 원본 URL로 리다이렉트한다.

이번 프로젝트에서는 기능 구현보다 다음 질문에 답하는 데 초점을 맞췄다.

* 단축 코드를 충돌 없이 어떻게 생성할 것인가?
* 읽기 요청이 늘면 어디에서 병목이 생기는가?
* Redis를 적용하면 DB 부하와 응답 시간이 얼마나 줄어드는가?
* Redis와 App 장애가 발생해도 요청을 계속 처리할 수 있는가?

---

## 2. 사용자 및 사용 시나리오

### 주요 사용자

* 사용자 유형: URL을 생성하는 사용자, 단축 URL에 접근하는 사용자
* 예상 사용 규모: 하루 URL 생성 1억 건, 읽기와 쓰기 비율 10:1로 가정
* 주요 사용 환경: 웹 브라우저 및 모바일 애플리케이션

### 핵심 시나리오

1. 사용자가 긴 URL을 전달한다.
2. 시스템이 고유한 단축 코드를 생성하고 저장한다.
3. 단축 URL을 반환한다.
4. 사용자가 단축 URL에 접근한다.
5. 원본 URL을 조회해 `302 Found`로 리다이렉트한다.

---

## 3. 기능 요구사항

| ID     | 요구사항                                               | 우선순위   |
| ------ |----------------------------------------------------| ------ |
| FR-001 | 긴 URL을 입력받아 단축 URL을 생성한다.                          | Must   |
| FR-002 | 단축 URL 요청을 원본 URL로 리다이렉트한다.                        | Must   |
| FR-003 | 단축 코드는 숫자와 영문 대소문자로 구성한다.                          | Must   |
| FR-004 | 서로 다른 URL에 동일한 단축 코드를 할당하지 않는다.                    | Must   |
| FR-005 | 유효하지 않은 URL 입력에는 400 응답을 반환한다.                     | Must   |
| FR-006 | 존재하지 않는 단축 코드에는 404 응답을 반환한다.                      | Must   |
| FR-007 | Sequence, Hash, Snowflake 기반 Base62 생성 방식을 비교한다.   | Should |
| FR-008 | Redis로 반복적인 원본 URL 조회를 캐시한다.                       | Should |
| FR-009 | Bloom Filter 또는 Negative Cache로 잘못된 코드의 반복 조회를 줄인다. | Could  |
| FR-010 | URL 수정과 삭제 기능은 제공하지 않는다.                           | Won't  |

우선순위 기준:

* Must: 반드시 구현
* Should: 핵심 실험을 위해 구현
* Could: 여유가 있을 때 구현
* Won't: 현재 범위에서 제외

---

## 4. 비기능 요구사항

### 성능

* 평균 예상 처리량: 쓰기 약 1,160 RPS, 읽기 약 11,600 RPS
* 로컬 실험에서는 절대 성능보다 동일 조건의 개선 전후 차이를 비교한다.
* Redis Warm Cache 목표: p95 100ms 이하, p99 200ms 이하
* 최대 허용 오류율: 1% 미만
* Redis 적용 후 DB 조회 수가 초기 구조보다 감소해야 한다.

### 가용성

- Redis 조회 실패 시 MySQL로 Fallback한다.
- Redis 장애 중에도 리다이렉트 오류율을 1% 미만으로 유지한다.
- Redis 장애가 반복되면 Circuit Breaker로 반복 Timeout을 줄인다.
- Redis 복구 후 Cache Aside 경로로 자동 복귀한다.
- Redis Master 장애 시 Sentinel이 Replica를 새로운 Master로 승격한다.
- Failover가 진행되는 동안 Circuit Breaker와 MySQL Fallback으로 요청을 처리한다.
- 단일 App 장애 시 Nginx가 다른 App 인스턴스로 GET 요청을 전환한다.

현재 남아 있는 주요 SPOF는 Nginx와 MySQL이다.

### 확장성

* 애플리케이션 서버는 무상태로 구성한다.
* App 인스턴스를 수평 확장할 수 있어야 한다.
* 다중 인스턴스에서는 Snowflake nodeId를 분리할 수 있어야 한다.
* 데이터 증가 시 DB Replica, Partitioning, Sharding을 검토할 수 있어야 한다.

### 정합성

* 단축 코드의 유일성은 강하게 보장한다.
* MySQL을 Source of Truth로 사용한다.
* Redis는 조회 캐시로 사용하며 최종적 정합성을 허용한다.
* 중복 코드는 DB Unique Constraint로 방지한다.

### 보안

* 별도의 사용자 인증과 인가는 구현하지 않는다.
* `http`, `https` URL만 허용한다.
* `javascript:`, `file:`, `data:` 프로토콜은 차단한다.
* 원본 URL 전체를 애플리케이션 로그에 남기지 않는다.
* Sequence 기반 코드의 예측 가능성을 트레이드오프로 분석한다.

---

## 5. 범위

### 포함

* URL 생성 API
* URL 리다이렉트 API
* URL 형식 검증
* MySQL 기반 Baseline
* Sequence, Hash, Snowflake 생성 전략 비교
* Redis Cache-Aside
* k6 부하 테스트와 Prometheus, Grafana 관측
* Redis 장애 시 MySQL Fallback
* Redis Circuit Breaker
* App 2개와 Nginx 기반 Failover
* 다중 인스턴스 Snowflake nodeId 검증
* Redis Master, Replica, Sentinel 구성
* Redis Master 자동 Failover 검증

### 제외

* 사용자 인증과 사용자별 URL 관리
* URL 수정과 삭제
* 클릭 통계와 데이터 분석
* Kafka 기반 이벤트 처리
* 실제 DB 샤딩과 Redis Cluster
* Kubernetes 및 다중 리전 배포

제외 항목은 현재 실험에서 직접 확인한 병복과 장애에 집중하기 위해 후속 과제로 남겼다.

---

## 6. 성공 기준

* [x] 긴 URL을 단축 URL로 변환할 수 있다.
* [x] 단축 URL을 통해 원본 URL로 리다이렉트할 수 있다.
* [x] 동시에 요청해도 중복 단축 코드가 저장되지 않는다.
* [x] MySQL 기반 Baseline 성능을 측정했다.
* [x] 병목을 서버와 지표를 통해 설명할 수 있다.
* [x] 개선 뒤 동일한 조건으로 재측정했다.
* [x] p95, p99, RPS, DB 조회 수를 비교했다.
* [x] Sequence, Hash, Snowflake 전략의 차이를 비교했다.
* [x] 다중 인스턴스에서 서로 다른 nodeId로 단축 코드 유일성을 검증했다.
* [x] 단일 App 장애 시 다른 인스턴스로 요청이 전환되는 것을 확인했다.
* [x] Redis 장애 시 MySQL Fallback으로 요청을 유지했다.
* [x] Circuit Breaker로 반복적인 Redis Timeout을 줄였다.
* [x] Redis Master 장애 시 Sentinel이 Replica를 자동 승격했다.
* [x] Sentinel Failover 과정에서도 리다이렉트 실패율 0%를 유지했다.
* [x] Failover 이후 Redis Cache 경로가 자동 복구되는 것을 확인했다.