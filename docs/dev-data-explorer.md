# Dev 데이터 조회 도구

dev 서버의 가짜 데이터를 두 가지 방식으로 확인한다.

- 매칭 조회 화면: 사람과 매칭 상태를 이해하기 쉽게 묶어서 확인
- Adminer: DB 테이블의 원본 row를 스프레드시트처럼 직접 확인

매칭 조회 화면은 조회 전용이다. Adminer는 원본 DB를 직접 다루는 도구라서 조회뿐 아니라 수정·삭제도 가능하다. 실제 매칭 수락·거절·결제 완료를 안전한 버튼으로 제공하는 기능은 이슈 #159에서 구현한다.

## 접속 주소

dev 서버 주소가 `https://dev-api.example.com`이라면 다음 경로를 사용한다.

| 목적 | 경로 |
| --- | --- |
| 테스트 페르소나 | `/dev/auth/console` |
| 매칭 상태 조회 | `/dev/matching/console` |
| DB 원본 조회 | `/adminer/` |

`local`, `dev` Spring profile에서만 매칭 조회 API와 화면이 등록된다. Adminer도 dev 전용 Compose에만 있고 host port를 직접 열지 않으므로 Caddy 경로로만 접근한다.

## 매칭 조회 화면 사용법

1. `/dev/matching/console`에 접속한다.
2. 왼쪽에서 현재 페이지의 요청을 검색하거나 상태로 거른다. 전체 페이지 통합 검색은 지원하지 않는다.
3. 요청을 선택하면 관련 강습생·강사와 request/group/item/offer/payment/lesson ID를 확인한다.
   `요청별 연결표`에서는 각 강습생의 request, group item, payment가 한 행으로 묶여 보인다.
   참가자 표에서는 원본 participant ID, request ID, 이름, 나이, 성별을 확인한다. V6 적용 전 row는 이름이 `null`일 수 있다.
4. `가능 동작`을 누르면 실제 변경 없이 다음 상태의 예상 결과를 확인한다.
5. 최신 데이터가 필요하면 `새로고침`을 누른다. 자동 새로고침은 필요할 때만 켠다.

응답의 `stateToken`은 참가자 이름을 포함한 현재 관련 row들의 상태를 대표하는 값이다. 이슈 #159에서는 버튼을 실제로 실행하기 직전에 서버가 이 값을 다시 검사해, 오래된 화면에서 잘못된 대상을 바꾸지 않도록 해야 한다.

### 요청이 0건일 때

`main` 자동 배포 직후나 `idle-base`로 reset한 뒤에는 매칭 요청이 0건인 것이 정상이다. Base seed는 페르소나와 강사 기본 설정만 만들고, 실제 매칭 요청은 만들지 않는다.

`매칭 요청`은 Seed가 대신 만들지 않는다. 공유 Dev의 `Reset Dev DB`는 안전을 위해 항상 `idle-base`로만 초기화하며, `matching-price-vivaldi` 같은 시나리오는 로컬과 CI에서만 사용한다. 공유 Dev 조회 화면에 요청을 만들려면 다음 순서로 실제 API를 호출한다.

1. GitHub Actions의 `Reset Dev DB`를 `main`에서 열고 `confirmReset`만 확인해 reset한다. Seed는 자동으로 `idle-base`가 적용된다.
2. `/dev/auth/console`에서 `대뜸GOAT-성빈-일반강습생` 페르소나의 소비자 토큰을 발급한다.
3. Swagger UI에서 `POST /api/v1/consumer/matching-requests`를 실행한다. 예시 요청 본문으로 `db/seed/scenarios/matching-price-vivaldi/request.json`을 사용해도 된다.
4. `/dev/matching/console`로 돌아와 `새로고침`을 누른다.

reset하면 기존 토큰과 고유 ID도 함께 무효가 될 수 있으므로, reset 뒤에는 토큰을 새로 발급한다.
강사 제안·결제 대기 같은 더 뒤 상태까지 확인하려면 공유 Dev에서는 강사 매칭 받기와 후속 동작을 일반 API로 순서대로 실행한다. Seed로 상태를 바로 준비해야 하면 공유 Dev가 아닌 로컬에서 해당 시나리오를 사용한다.

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

- 애플리케이션 schema·데이터 변경: 없음 (`NO_MIGRATION_REQUIRED`, `DB_CONSUMER_ONLY`)
- Adminer 권한: 기존 `admin` 계정을 재사용하므로 조회·수정·삭제 가능
- 로컬 MySQL/API/배포 파일 계약: 자동 테스트 가능
- 실제 shared dev RDS 자동 로그인: main 반영 뒤 dev 배포에서 확인 필요 (`SHARED_ENV_UNPROVEN`)
