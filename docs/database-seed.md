# 테스트 Seed 사용 가이드

## 한눈에 보기

테스트 seed는 두 층으로 나뉜다.

1. `db/seed/base`: 모든 dev/test QA 시나리오가 공통으로 참조하는 기반 데이터
2. `db/seed/scenarios`: 테스트 목적에 따라 골라 넣는 시나리오 데이터

앱이 모든 환경에서 반드시 필요로 하는 정책·기준 데이터는 seed가 아니라 Flyway migration이
소유한다.

로컬 DB를 초기화할 때는 SQL 파일을 직접 하나씩 실행하지 말고 reset 스크립트를 사용한다.
스크립트가 `clean → migrate → base → scenario → verify` 순서를 보장한다.

로컬 reset 스크립트는 로컬 DB에만 사용한다. 공유 dev DB는 `main` 자동 배포 또는
`Reset Dev DB` GitHub Actions workflow로만 초기화한다. 아직 Dev 데이터를 보존하지 않는
동안에는 main 배포마다 `clean → migrate → validate → base → verify`를 실행한다. 운영 DB에는
로컬·dev reset 스크립트와 workflow를 모두 사용할 수 없다.

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
| `matching-multi-request-oak` | FLOW / STABLE | 한 소비자가 활성 요청을 취소하며 요청 4건과 참가자 16명의 이력을 순차 생성 | 동시 활성 요청은 409로 거절 |
| `pm-full-requested-catalog` | SNAPSHOT / TRANSITION | PM 스프레드시트 전체 입력 상태 조회와 dev QA 놀이터 | 로컬/CI snapshot은 scheduler OFF, 공유 dev는 검증 뒤 scheduler ON |

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

## 공유 dev DB에서 QA 상태 만들기

공유 dev DB는 명령어를 직접 입력하지 않고 GitHub Actions의 `Reset Dev DB`에서 실행한다.
main 자동 배포 직후에는 base seed만 있는 초기 상태이며, 특정 QA 시나리오가 필요할 때 이
workflow를 추가로 실행한다.

1. `main` ref에서 실행할 시나리오를 고른다.
2. 기존 dev 데이터를 지우는 `confirmReset`을 체크한다.
3. workflow가 별도 승인 없이 앱 중지 → clean → migrate → base → scenario → verify → 앱 재기동을 실행한다.
4. 결과 요약이 성공인지 확인한 뒤 새로 로그인해 QA를 시작한다.

실행 권한은 저장소에 write 권한이 있는 사람으로 제한한다. `dev-reset` Environment는 사람
승인 단계가 아니라 `main` 제한과 reset용 인프라 설정 경계로 사용한다. 앱 runtime 계정은
일반 DML 용도로 분리하고, migration 계정 하나가 Flyway migration과 dev clean/seed를
함께 수행한다.

자동 배포와 수동 reset은 dev DB 전체를 다시 만들기 때문에 기존 access token과 resource ID를
계속 사용하면 안 된다. `pm-full-requested-catalog`를 골랐다면 새 QA는 요청이 없는
`qa-free-consumer`로 시작한다. 이 시나리오는 canonical snapshot을 먼저 검증한 뒤 dev 전용
playground 데이터를 더하고, scheduler 기본 설정을 켠 채 앱을 재기동하므로 시간이 지나면서
상태가 자연스럽게 바뀔 수 있다.

결과 report에 incomplete marker가 남았다고 나오면 부분 DB일 수 있으므로 앱을 직접 켜지
않는다. 수동 reset은 기존 marker가 있으면 중단한다. 자동 배포는 현재 main SHA와 dev DB
대상을 다시 확인한 뒤 전체 clean부터 재실행해 복구하며, 성공 전까지 marker가 앱 재기동을
막는다. 자동 배포가 clean을 시작하기 전에는 후보 Compose 문법, image pull, DB 연결, Flyway
실행 가능 여부를 먼저 확인한다. 이 사전 검증이 실패하면 기존 앱과 DB는 그대로 둔다. report
자체를 가져오지 못한 경우에는 reset 미시작·진행 중·완료를 단정할 수 없다.
먼저 EC2 marker, 앱 컨테이너, DB 상태를 확인한다.

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

전체 snapshot은 요청 16건을 보존하되, 소비자별 `_a` 요청 9건만 `REQUESTED`로 둔다.
같은 소비자의 나머지 반복 입력 7건은 활성 협상 1건 정책에 맞춰 `CANCELED` 이력으로
정규화했다. 조건이 맞는 강사가 있어 scheduler가 실행되면 활성 요청 일부가 `GROUPED`로
바뀔 수 있다. 로컬에서 정확한 시작 snapshot을 조회할 때만 다음처럼 scheduler를 끄고
서버를 실행한다. 공유 dev QA에서는 이 옵션을 사용하지 않는다.

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

이 테스트는 실제 handshake·인증·개인 큐 구독·메시지 직렬화를 확인하지만, 재접속
자체는 확인하지 않는다. 소비자는 [#79](https://github.com/TEAM-SSING/SSING-SERVER/issues/79)의
활성 매칭 조회 API로 이벤트 유실 뒤 현재 상태를 복구하고, 강사 복구는
[#86](https://github.com/TEAM-SSING/SSING-SERVER/issues/86)에서 별도로 다룬다. 이벤트는 화면
갱신 신호이고 최종 상태의 기준은 REST/DB라는 원칙은 그대로 유지한다.

## 자동 검증과 CI

필수 CI의 두 Runner는 각각 integration test JVM 하나와 MySQL 8.4.8 컨테이너 하나를 사용한다.
테스트 클래스마다 컨테이너를 다시 만들거나 Testcontainers reuse 기능을 사용하지 않는다.
DB 테스트는 한 JVM 안에서 순차 실행한다.

`DatabaseBootstrapContractTest`는 빈 DB를 V1부터 최신까지 한 번 migration하고 validate한다.
그 뒤 `DatabaseCleaner`가 `flyway_schema_history`와 migration 소유 필수 데이터인
`platform_fee_policies`를 보존하면서 애플리케이션 테이블만 비우는지 두 번 반복해 확인한다.

`DatabaseSeedContractTest`는 최신 schema를 그대로 두고 각 scenario 전에 데이터만 비운 뒤
base seed를 다시 적용한다. 다음 조건은 그대로 검증한다.

- scenario의 `scenario.yml`, `seed.sql`, `verify.sql` 존재 확인
- scenario SQL과 검증 SQL 실행
- `integration-test` 공통 프로필에서 모든 `@Scheduled` 자동 실행 차단
- 실제 매칭 scheduler 빈의 `runScheduledSearch()`를 수동 실행
- PM snapshot의 103개 원본 매핑과 실제 DB 관계를 비교한 뒤, 조건이 맞는 2건의 전이와 재실행 멱등성 확인

통합 테스트가 `@Scheduled` 자동 등록을 끄는 이유는 테스트가 60초 안에 끝나기를 기대하지 않고,
상태를 바꾸는 시점을 테스트 코드가 직접 소유하기 위해서다. 실제 매칭 scheduler 빈은 수동
호출하므로 운영 진입점부터 DB 상태 전이까지 검증한다. 전역 예약 작업이 꺼져 있어도 실제
WebSocket 통합 테스트는 통과하므로 STOMP heartbeat와 업무 `@Scheduled` 작업도 분리해 검증한다.
Scheduler Bean 설정 자체는 DB가 필요 없는 단위 테스트로 분리했다. V4의 활성 3개·이력 5개
제약은 서로 다른 회원을 사용해 schema/data 준비 한 번으로 모두 확인한다.

V3→V4와 V4→V5의 기존 데이터 보존 테스트는 삭제하지 않았다. Dev 데이터를 폐기할 수 있는
현재에는 PR 필수 경로에서 제외하고 `Legacy Migration Compatibility` nightly 또는 수동
workflow로 실행한다.

```bash
./gradlew legacyMigrationTest
```

운영 데이터가 생기기 전에는 이 두 테스트를 PR 필수 CI로 다시 올려야 한다. 새 migration과
빈 DB V1→최신 bootstrap 검증은 계속 필수 CI에 남는다.

`db-seed-check.yml`은 disposable 로컬 MySQL에 `reset-all-local.sh`을 한 번 실행해 모든
시나리오 reset 경로를 검증한다. 같은 검증이 PR마다 세 번째 Runner로 붙어 대기 시간을 늘리지
않도록 nightly와 수동 실행으로만 유지한다. 통합 테스트와 빠른 DB 실행기 계약은 CI/CD의 두
Gradle Runner에서 계속 필수로 실행한다.

`test-dev-runner-contract.sh`와 `test_dev_workflow_contract.py`는 실제 dev DB 대신 가짜
명령과 workflow 구조를 사용해 대상 allowlist, UTF-8 client 설정, DB 주소 마스킹, 실패
종료 코드, incomplete marker, 한글 복구 안내를 검증한다. 자동 테스트는 공유 dev DB를
초기화하지 않는다.

main 배포에서는 애플리케이션 테스트, DB·Seed 테스트, ARM64 이미지 build·push가 동시에
시작한다. 세 작업이 모두 성공해야 Dev DB를 초기화하고 앱을 재기동한다. 이미지는
`dev-{commit SHA}` 태그로 찾되 실제 배포 설정은 registry digest까지 고정한다. migration과
seed 파일도 `releases/{commit SHA}`의 빈 snapshot에 설치해 이전 배포에서 삭제된 SQL이 섞이지
않게 한다. 후보 Compose·image·Flyway 사전 검증 뒤 DB clean 직전과 Compose 재시작 직전에 현재
main SHA를 다시 확인한다. 재시작은 사전 검증에서 받은 image만 사용하고 다시 pull하지 않는다.

## 현재 범위 밖의 후속 작업

- 로컬 통합 CLI·doctor·전체 CI gate와 branch protection: [#122](https://github.com/TEAM-SSING/SSING-SERVER/issues/122)
- 강사 메인 재진입 복구: [#86](https://github.com/TEAM-SSING/SSING-SERVER/issues/86)
- 100건을 넘는 재탐색 공정성: [#123](https://github.com/TEAM-SSING/SSING-SERVER/issues/123)
- 다중 요청 그룹 결제와 강습비 분담/반올림 정책: 현재 MVP 범위 밖

공유 dev의 첫 실제 reset 전에는 Environment의 `main` 제한, migration/reset 공용 계정 권한, 대상
fingerprint, 백업 또는 snapshot 정책을 운영자가 별도로 확인해야 한다. 이 문서와 자동
계약 테스트만으로 실제 RDS rollout이 검증됐다고 보지 않으며, `baselineOnMigrate`도 자동
활성화하지 않는다.
