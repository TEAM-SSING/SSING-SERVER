# Dev 데이터 조회 도구

dev 서버의 가짜 데이터를 세 가지 방식으로 확인한다.

- 매칭 조회 화면: 사람과 매칭 상태를 이해하기 쉽게 묶어서 확인
- 실제 Kakao 로그인 회원: 강사 신청·승인·설정·매칭 노출 상태를 조회하고 단계별로 변경
- Adminer: DB 테이블의 원본 row를 스프레드시트처럼 직접 확인

매칭 화면은 관계 조회와 제한된 상태 동작을 함께 제공한다. Adminer는 원본 DB를 직접 다루는 도구라서 조회뿐 아니라 수정·삭제도 가능하다.

매칭 화면에서 실제 실행할 수 있는 범위는 단일 요청 그룹의 아래 3개다.

- 강사 수락
- 강습생 수락
- 결제 완료

강사·강습생 거절과 다중 요청 그룹 동작은 결과 분기가 크므로 영향 미리보기만 제공한다.

## 접속 주소

dev 서버 주소가 `https://dev-api.example.com`이라면 다음 경로를 사용한다.

| 목적 | 경로 |
| --- | --- |
| 테스트 페르소나 | `/dev/auth/console` |
| 실제 Kakao 회원 강사 관리 | `/dev/auth/console`의 `실제 Kakao 로그인 회원` 영역 |
| 매칭 상태 조회·동작 | `/dev/matching/console` |
| DB 원본 조회 | `/adminer/` |

`local`, `dev` Spring profile에서만 매칭 조회 API와 화면이 등록된다. Adminer도 dev 전용 Compose에만 있고 host port를 직접 열지 않으므로 Caddy 경로로만 접근한다.

## 실제 Kakao 회원 강사 관리

강사 앱에서 Kakao 로그인을 한 회원은 `/dev/auth/console`의 아래쪽 목록에 나타난다. 이 목록은 합성 페르소나가 아니라 `oauth_accounts.provider = KAKAO`인 실제 dev 회원만 보여준다.

각 행에서 OAuth 계정, 회원, 강사 프로필, 매칭 설정, 활성 가격 정책의 내부 ID와 현재 상태를 확인할 수 있다. Kakao의 외부 사용자 식별값(`providerUserId`)은 이 작업에 필요하지 않아 응답과 화면에 노출하지 않는다.

상태에 따라 다음 버튼만 서버가 골라서 보여준다.

1. `테스트 신청 만들기`: 강사 프로필이 없는 ACTIVE 강습생을 승인 대기(`PENDING`)로 만든다.
2. `승인하고 설정 저장`: 승인 대기 프로필을 승인하고 회원 역할을 강사로 바꾸며, 선택한 리조트·종목·레벨·시간·인원·가격을 한 번에 저장한다. 이때 매칭 노출은 아직 OFF다.
3. `설정 다시 저장`: 노출 OFF인 승인 강사의 설정과 활성 가격 정책을 교체한다.
4. `매칭 시작`: 저장된 설정을 검증한 뒤 새 매칭 요청의 후보에 들어가도록 노출을 ON으로 바꾼다.
5. `매칭 중단`: 앞으로의 후보 노출만 OFF로 바꾼다. 이미 만들어진 제안이나 결제 금액은 취소·변경하지 않는다.

리조트, 종목, 레벨, 시간, 인원은 칩으로 선택하고 가격은 슬라이더 또는 `-`/`+` 버튼으로 고른다. 키보드 입력은 필요 없다. 신청에 필요한 나머지 값은 모든 테스트 회원에 아래처럼 동일하게 넣고, 실행 전에 화면에 안내한다.

- 실명: Kakao 닉네임
- 전화번호: `010-0000-0000`
- 성별·생년월일: `MALE`, `2000-01-01`
- 소개·경력 시작일: 개발용 고정 문구, `2020-01-01`
- 자격증: 선택 종목의 테스트 Level 1 자격증 추가(기존 자격증 보존)
- 장비 준비: 항상 true

승인, 역할 변경, 설정, 가격 저장은 하나의 DB 트랜잭션으로 처리한다. 중간 저장에 실패하면 앞에서 변경한 값도 함께 되돌린다. 목록에서 받은 `stateToken`도 실행 직전에 다시 확인하므로 다른 개발자가 먼저 바꾼 상태라면 `409 DEV_INSTRUCTOR_STATE_CHANGED`를 반환하고 최신 목록을 다시 읽는다.

이 기능은 회원 삭제를 제공하지 않는다. broad delete는 연관 데이터 범위가 커서 개발 확인 도구의 간단한 상태 전이 범위에서 제외한다.

실제 Kakao 회원 조회·상태 변경 API는 아래 두 조건을 모두 만족할 때만 생성된다.

- Spring profile이 `local` 또는 `dev`
- `SSING_DEV_INSTRUCTOR_ACTIONS_ENABLED=true`

기본값은 false이며 production profile에서는 변수가 true여도 API가 생성되지 않는다. 진행 중인 소비자 매칭 또는 확정 강습이 있는 회원은 역할을 강사로 바꾸면 기존 흐름에 접근하지 못할 수 있으므로 승인 버튼을 숨기고 서버에서도 승인을 거절한다.

## 상태 변경 기능 켜기

두 상태 변경 기능은 서로 독립된 GitHub Actions Variables로 켠다. 비밀값이 아니므로 Secret에 넣지 않는다.

| 기능 | GitHub Actions Variable | 기본값 |
| --- | --- | --- |
| 단일 매칭 수락·결제 | `SSING_DEV_MATCHING_ACTIONS_ENABLED` | `false` |
| 실제 Kakao 회원 강사 관리 | `SSING_DEV_INSTRUCTOR_ACTIONS_ENABLED` | `false` |

Repository의 `Settings → Secrets and variables → Actions → Variables`에서 필요한 값을 `true`로 바꾸고 dev를 다시 배포하면 된다. 값이 없거나 `false`면 deploy workflow가 런타임 `.env`에 `false`를 기록한다.

- 매칭 조회 GET과 `/dev/matching/console`은 플래그가 꺼져도 계속 사용할 수 있다. 동작은 모두 `미리보기만`으로 표시되고 POST 경로는 등록되지 않는다.
- 실제 Kakao 회원 영역은 강사 관리 플래그가 켜진 local/dev에서만 조회·변경 API가 등록된다.
- 두 상태 변경 API는 production에서 등록되지 않고 Swagger `Try it out`에도 나타나지 않는다.

## 매칭 조회 화면 사용법

1. `/dev/matching/console`에 접속한다.
2. 왼쪽에서 현재 페이지의 요청을 검색하거나 상태로 거른다. 전체 페이지 통합 검색은 지원하지 않는다.
3. 요청을 선택하면 관련 강습생·강사와 request/group/item/offer/payment/lesson ID를 확인한다.
   `요청별 연결표`에서는 각 강습생의 request, group item, payment가 한 행으로 묶여 보인다.
   참가자 표에서는 원본 participant ID, request ID, 이름, 나이, 성별을 확인한다. V6 적용 전 row는 이름이 `null`일 수 있다.
4. `가능 동작`을 누르면 Actor와 영향을 받는 사람·원본 row, 예상 상태 변화를 확인한다.
5. `실행 가능` 동작은 확인창의 실행 버튼을 누르면 실제 dev DB 상태가 바뀐다. `미리보기만` 동작은 DB를 바꾸지 않는다.
6. 성공하면 목록과 상세를 자동으로 다시 조회한다. 최신 데이터가 더 필요하면 `새로고침`을 누른다.

응답의 `stateToken`은 현재 관련 row들의 상태를 대표하는 값이다. 버튼을 실행하기 직전에 서버가 이 값을 다시 검사한다. 다른 개발자가 먼저 상태를 바꿨다면 `409 DEV_MATCHING_STATE_CHANGED`를 반환하고, 화면은 최신 상세를 자동으로 다시 조회한다.

화면은 actor의 member ID나 offer ID를 입력받지 않는다. 서버가 현재 request 관계에서 실행자와 대상을 찾아 기존 운영 매칭 Service를 호출하므로, 개발 도구가 별도 상태 전이 규칙을 복제하지 않는다.

### 요청이 0건일 때

`main` 자동 배포 직후나 `idle-base`로 reset한 뒤에는 매칭 요청이 0건인 것이 정상이다. Base seed는 페르소나와 강사 기본 설정만 만들고 실제 매칭 요청은 만들지 않는다.

공유 Dev의 `Reset Dev DB`는 안전을 위해 항상 `idle-base`로만 초기화한다. `matching-price-vivaldi` 같은 시나리오는 로컬과 CI에서만 사용한다. 공유 Dev에서 조회할 요청은 다음 순서로 실제 API를 호출해 만든다.

1. GitHub Actions의 `Reset Dev DB`를 `main`에서 열고 `confirmReset`을 확인해 reset한다.
2. `/dev/auth/console`에서 소비자 페르소나 토큰을 발급한다.
3. Swagger UI에서 `POST /api/v1/consumer/matching-requests`를 실행한다. 요청 예시는 `db/seed/scenarios/matching-price-vivaldi/request.json`을 참고할 수 있다.
4. `/dev/matching/console`에서 `새로고침`을 누른다.

reset하면 기존 토큰과 고유 ID도 함께 무효가 될 수 있으므로 토큰을 다시 발급한다. 제안·결제 대기 같은 뒤 상태는 일반 API 또는 이 콘솔의 허용된 동작을 순서대로 실행해 만든다.

관계가 비정상인 요청은 목록 전체를 실패시키지 않고 다음처럼 표시한다.

- `resolutionState`: `INCONSISTENT`
- `matchingStatus`: `null`
- `diagnostics`: 어떤 관계가 잘못됐는지 설명
- `availableActions`: 빈 배열

개발 중 자주 생기는 다음 관계 오류를 간단한 규칙으로 함께 확인한다.

- 한 요청이 여러 활성 그룹에 동시에 들어간 경우
- 한 그룹이나 한 강사에 여러 live 제안이 동시에 남은 경우
- 제안 가격 snapshot 또는 참가자 row 수가 맞지 않는 경우
- 현재 단계보다 이른 결제·강습 row가 있거나, 확정 단계의 결제·강습 상태가 맞지 않는 경우

모든 과거 enum 조합을 감사하는 도구는 아니다. 위 대표 오류가 발견되면 잘못된 실행 미리보기를 보여주지 않는 것을 우선한다.

## Adminer 자동 로그인

`/adminer/`에 접속하면 별도 입력 없이 `ssing` DB로 자동 로그인한다.

- 계정: 기존 dev runtime `admin` 계정 재사용
- 계정 생성: 필요 없음
- Actions 추가 설정: 필요 없음. 기존 `SSING_DEV_DATASOURCE_USERNAME`, `SSING_DEV_DATASOURCE_PASSWORD`를 재사용
- 비밀번호 전달: GitHub Secret에서 EC2의 `secrets/adminer-db-password` 파일로 복원한 뒤 Adminer 컨테이너에만 읽기 전용 마운트
- 브라우저 전달값: 실제 비밀번호가 아닌 자동 로그인 표시값만 전송

따라서 개발자는 계정이나 비밀번호를 복사할 필요가 없다. 단, `admin` 계정이라 테이블 row 수정·삭제와 DDL 실행도 가능하다. `/adminer/`에서는 실수로 저장 버튼을 누르지 않도록 주의한다.

로그아웃하면 다음 접속에서 다시 자동 로그인된다. 자동 로그인 자체를 끄려면 Adminer 서비스를 내리거나 자동 로그인 플러그인 마운트를 제거해야 한다.

## 배포 후 확인

배포 workflow는 앱 내부 health가 정상일 때 새 `.env`를 먼저 현재 설정으로 승격한다. 그다음 외부 HTTPS 앱 health를 확인하고, 마지막으로 `/adminer/` 자동 로그인을 별도 검사한다. Adminer 검사만 실패해도 workflow는 실패하지만, 이미 정상 기동한 앱의 `.env`를 되돌리거나 후보 설정 파일을 잃어버리지는 않는다.

최초 배포 뒤 브라우저에서 아래 순서를 한 번 확인한다.

1. `/adminer/` 접속 시 로그인 화면을 거치지 않는지 확인
2. `members` 같은 테이블 목록 열기
3. 조건 검색과 정렬 실행
4. row와 고유 ID 확인
5. 로그아웃 뒤 다시 접속하면 자동 로그인되는지 확인

Caddy의 `/adminer/` 하위 경로에서 로그인 이후 링크나 redirect가 깨진다면, Adminer를 `adminer-dev.example.com` 같은 dev 전용 subdomain으로 분리한다. 이 경우에도 host port는 열지 않고 Caddy를 통해서만 접근한다.

## 현재 검증 경계

- 애플리케이션 schema 변경: 없음 (`NO_MIGRATION_REQUIRED`, `DB_CONSUMER_ONLY`)
- 실제 Kakao 회원 강사 관리: 신청·승인+설정·설정 재저장·매칭 시작/중단만 허용, 회원 삭제 없음
- 강사 상태 변경 격리: `local`/`dev` profile과 기본 false 기능 플래그의 이중 조건, production 미등록, Swagger 숨김
- 역할 변경 보호: 진행 중 소비자 매칭 또는 확정 강습이 있으면 강사 승인 차단, 잠금 뒤 소비자 매칭 생성 시 역할 재검증
- 매칭 화면 상태 변경: 단일 요청의 수락·결제 3개만 허용, 거절·다중 요청은 미리보기만 제공
- 배포 노출: 두 기능은 GitHub Actions Variables로 각각 켜며 값이 없으면 false, 상태 변경 API는 Swagger에서 숨김
- Adminer 권한: 기존 `admin` 계정을 재사용하므로 조회·수정·삭제 가능
- 로컬 MySQL/API/배포 파일 계약: 자동 테스트 가능
- 실제 shared dev RDS 자동 로그인: main 반영 뒤 dev 배포에서 확인 필요 (`SHARED_ENV_UNPROVEN`)
