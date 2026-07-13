# 테스트 Seed 사용 가이드

## 한눈에 보기

테스트 seed는 두 층으로 나뉜다.

1. `db/seed/base`: 앱이 반드시 필요로 하는 공통 데이터
2. `db/seed/scenarios`: 테스트 목적에 따라 골라 넣는 시나리오 데이터

로컬 DB를 초기화할 때는 SQL 파일을 직접 하나씩 실행하지 말고 reset 스크립트를 사용한다.
스크립트가 `clean → migrate → base → scenario → verify` 순서를 보장한다.

공유 dev DB와 운영 DB에는 이 스크립트를 사용할 수 없다. dev 적용은
[#122](https://github.com/TEAM-SSING/SSING-SERVER/issues/122)에서 별도로 관리한다.

## 데이터 구성

### Base seed

- 스키장 9곳과 리프트권 금액
- API 흐름 검증용 익명 소비자 `consumer-default`
- 승인 강사 `instructor-approved-default`

활성 플랫폼 수수료 정책 1건(현재 0%)은 Base seed가 아니라 Flyway migration이 소유한다.
따라서 새 DB는 migration만 실행해도 필수 정책이 생기고, Base seed는 그 이후 테스트
시나리오가 공통으로 참조할 데이터만 넣는다.

### Scenario seed

| scenario key | 종류 | 용도 | 주의점 |
| --- | --- | --- | --- |
| `matching-price-vivaldi` | FLOW / STABLE | 매칭 요청부터 85,000원 결제와 강습 확정까지 검증 | 기본 골든 플로우 |
| `matching-no-candidate-alpensia` | FLOW / STABLE | 후보가 없을 때 `SEARCHING` 유지 검증 | 즉시 `FAILED`가 되지 않음 |
| `matching-multi-request-oak` | FLOW / STABLE | 한 소비자가 요청 4건과 참가자 16명을 독립 생성 | 다중 요청 묶음 결제는 MVP 제외 |
| `pm-full-requested-catalog` | SNAPSHOT / TRANSITION | PM 스프레드시트 전체 입력 상태 조회 | 앱 실행 시 scheduler를 꺼야 함 |

`FLOW`는 SQL로 시작 조건만 만들고 REST API로 상태를 바꾼다. `SNAPSHOT`은 PM이 준
입력을 특정 시점의 DB 상태로 바로 구성한다. 따라서 `pm-full-requested-catalog`가
정상 API 흐름을 모두 거쳤다는 뜻은 아니다.

## 로컬에서 시나리오 하나 사용하기

필수 조건:

- Docker와 Docker Compose가 실행 중이어야 한다.
- 보존해야 할 로컬 DB 데이터가 없어야 한다.
- 초기화 중에는 로컬 Spring 서버를 꺼야 한다.

기본 골든 플로우를 적용한다.

```bash
./scripts/db/reset-local.sh --confirm-local-reset matching-price-vivaldi
```

다른 시나리오는 마지막 key만 바꾼다.

```bash
./scripts/db/reset-local.sh --confirm-local-reset matching-no-candidate-alpensia
./scripts/db/reset-local.sh --confirm-local-reset matching-multi-request-oak
./scripts/db/reset-local.sh --confirm-local-reset pm-full-requested-catalog
```

모든 시나리오가 각각 깨끗한 DB에서 적용되는지 한 번에 확인하려면 다음 명령을 쓴다.
마지막에는 알파벳순으로 가장 뒤인 `pm-full-requested-catalog` 상태가 DB에 남는다.

```bash
./scripts/db/reset-all-local.sh --confirm-local-reset
```

개별 seed SQL은 strict insert를 사용한다. 같은 DB에 SQL 파일만 두 번 실행하지 않는다.
재실행이 필요하면 전체 reset 명령을 다시 실행한다.

## 로컬 서버와 토큰 사용하기

최초 한 번 로컬 설정 파일을 만든다.

```bash
cp config/application-local.example.yml application-local.yml
```

서버를 실행한 뒤 브라우저에서 `http://localhost:8080/dev/auth/console`을 열거나 API를
직접 호출한다.

```bash
curl -X POST http://localhost:8080/dev/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"personaKey":"consumer-default"}'

curl -X POST http://localhost:8080/dev/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"personaKey":"instructor-approved-default"}'
```

응답의 access token을 REST 요청의 `Authorization: Bearer <token>`에 넣는다.
사용 가능한 persona는 `GET /dev/auth/personas`에서 확인한다.

## PM 전체 스프레드시트 확인하기

`pm-full-requested-catalog`는 PM 원본 105행을 모두 추적한다.

- 스키장 9건
- 회원 13건
- 매칭 요청 16건
- 참가자 49건
- 강사 프로필 4건
- 매칭 설정 4건
- 가격 정책 4건
- 자격 원본 6건

현재 스키마로 표현 가능한 103행은 seed에 반영했다. 지원 enum이 없는 아래 자격 2행은
억지 값으로 바꾸지 않고 `source-mapping.tsv`에 제외 사유를 기록했다.

- `instructor_certification_003`: `TEAM_INTERNAL BASIC`
- `instructor_certification_005`: `ATHLETE_CAREER`

실명·전화번호·생년월일·원문 persona 서사는 실행 SQL에 복사하지 않고 익명 기술
fixture로 치환했다. 구조화된 승인/자격 값과 PM 설명이 충돌하는 강사 1명은
`MAPPED_REVIEW_REQUIRED`로 표시해 실제 페르소나 적합성 판단과 분리했다.

전체 snapshot의 매칭 요청은 모두 `REQUESTED` 시작 상태다. 조건이 맞는 강사가 있어
scheduler가 실행되면 일부 요청이 `GROUPED`로 바뀔 수 있다. 원본 상태를 조회하려면
다음처럼 scheduler를 끄고 서버를 실행한다.

```bash
SSING_SCHEDULED_JOBS_ENABLED=false \
SSING_MATCHING_SEARCH_SCHEDULER_ENABLED=false \
./gradlew bootRun
```

원본 행과 seed 행의 대응 관계는 다음 파일이 기준이다.

```text
db/seed/scenarios/pm-full-requested-catalog/source-mapping.tsv
db/seed/scenarios/pm-full-requested-catalog/expected-snapshot.tsv
```

`source-mapping.tsv`는 원본 105행의 반영·제외 판단을 기록한다. `expected-snapshot.tsv`는
반영된 103행을 리조트 9개, 소비자 9명, 강사 4명, 요청 16개의 38개 DB 묶음으로 연결한다.
통합 테스트는 이 계약과 실제 MySQL의 소비자·리조트·종목·레벨·가격·자격·참가자 관계를
모두 비교한다. 따라서 총개수는 같아도 서로 잘못 연결된 seed는 실패한다.

## WebSocket 검증 방법

WebSocket 이벤트 자체는 seed SQL에 저장할 수 없다. 대신 통합 테스트가 실제 임의
포트에 접속해 다음 순서를 자동 검증한다.

1. STOMP `CONNECT`에 seed 사용자의 access token을 전달한다.
2. 강사가 `/user/queue/matching`을 구독한다.
3. 소비자가 REST로 매칭을 요청하고 강사가 `MATCHING_OFFER_RECEIVED`를 받는다.
4. 결제와 강습 생성 후 소비자·강사가 `/user/queue/lesson`을 구독한다.
5. 두 사용자가 `LESSON_STARTED`, `LESSON_COMPLETED`를 받는다.
6. 마지막 REST 조회와 DB 상태도 `COMPLETED`인지 확인한다.

WebSocket 통합 테스트만 실행한다.

```bash
./gradlew integrationTest \
  --tests 'org.sopt.ssingserver.database.SeedRealtimeFlowIntegrationTest'
```

전체 seed 계약과 REST/WebSocket 흐름을 함께 실행한다.

```bash
./gradlew integrationTest
```

이 테스트는 실제 handshake·인증·개인 큐 구독·메시지 직렬화를 확인하지만, 재접속과
이벤트 유실 복구는 확인하지 않는다. 재접속은 [#79](https://github.com/TEAM-SSING/SSING-SERVER/issues/79),
[#86](https://github.com/TEAM-SSING/SSING-SERVER/issues/86),
[#109](https://github.com/TEAM-SSING/SSING-SERVER/issues/109)의 후속 범위다. 이벤트는 화면
갱신 신호이고 최종 상태의 기준은 REST/DB라는 원칙은 그대로 유지한다.

## 자동 검증과 CI

`DatabaseSeedContractTest`는 모든 scenario 디렉터리를 찾아 각각 다음 조건으로 검증한다.

- 깨끗한 MySQL 8.4.8에서 migration과 base seed 적용
- scenario의 `scenario.yml`, `seed.sql`, `verify.sql` 존재 확인
- scenario SQL과 검증 SQL 실행
- `integration-test` 공통 프로필에서 모든 `@Scheduled` 자동 실행과 매칭 scheduler 빈 차단
- 대표 FLOW는 실제 업무 진입점인 `MatchingSearchTriggerService`를 명시적으로 실행
- PM snapshot의 103개 원본 매핑과 실제 DB 관계를 비교한 뒤, 조건이 맞는 2건의 전이와 재실행 멱등성 확인

통합 테스트가 scheduler를 끄는 이유는 테스트가 60초 안에 끝나기를 기대하지 않고, 상태를
바꾸는 시점을 테스트 코드가 직접 소유하기 위해서다. 전역 예약 작업이 꺼져 있어도 실제
WebSocket 통합 테스트는 통과하므로 STOMP heartbeat와 업무 `@Scheduled` 작업도 분리해 검증한다.

`db-seed-check.yml`은 로컬과 같은 `reset-all-local.sh`을 두 번 실행해 전체 reset의
재실행 안전성을 확인하고, 별도의 Testcontainers DB에서 통합 테스트를 실행한다.

## 현재 범위 밖의 후속 작업

- 공유 dev DB migration/seed 적용: [#122](https://github.com/TEAM-SSING/SSING-SERVER/issues/122)
- WebSocket 재접속과 이벤트 유실 복구: [#109](https://github.com/TEAM-SSING/SSING-SERVER/issues/109)
- 100건을 넘는 재탐색 공정성: [#123](https://github.com/TEAM-SSING/SSING-SERVER/issues/123)
- 다중 요청 그룹 결제와 강습비 분담/반올림 정책: 현재 MVP 범위 밖

현재 비어 있는 dev DB는 #122에서 V1부터 fresh migration하는 방향이다. 비어 있지 않은
기존 DB를 다룰 때만 live schema 비교·snapshot·명시적 baseline 절차를 별도로 설계하며,
`baselineOnMigrate`는 자동 활성화하지 않는다.
