#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/dev-common.sh"

CURRENT_STAGE="입력 검증"
LAST_SUCCESS_STAGE="없음"
APP_STOPPED=false
CLEAN_STARTED=false
DB_VERIFIED=false
HEALTH_VERIFIED=false
MARKER_CREATED=false
MARKER_PREEXISTED=false
APP_STATE="기존 상태 유지"
DB_STATE="변경 없음"
SAFE_SCENARIO="미확정"
REPORT_RESULT="실패"

readonly DEPLOY_DIR="$(dev_deploy_dir)"
readonly COMPOSE_FILE="${SSING_DEV_COMPOSE_FILE:-docker-compose.dev.yml}"
readonly REPORT_PATH="${SSING_DEV_RESET_REPORT_PATH:-$DEPLOY_DIR/.dev-reset-report.md}"

if [[ -e "$(dev_reset_marker_path)" ]]; then
  MARKER_PREEXISTED=true
  DB_STATE="이전 reset의 부분 또는 미확인 상태"
fi

assert_dev_reset_artifacts() {
  local scenario_key="$1"
  local scenario_directory="$PROJECT_ROOT/db/seed/scenarios/$scenario_key"
  local -a base_files=("$PROJECT_ROOT/db/seed/base"/*.sql)
  local -a migration_files=("$PROJECT_ROOT/src/main/resources/db/migration"/*.sql)
  local -a required_files=(
    "$scenario_directory/seed.sql"
    "$scenario_directory/verify.sql"
    "$PROJECT_ROOT/db/seed/verify-utf8.sql"
  )

  [[ "$scenario_key" =~ ^[a-z0-9]+(-[a-z0-9]+)*$ && -d "$scenario_directory" ]] \
    || dev_fail "선택한 Seed 시나리오 디렉터리가 없습니다."
  [[ -s "${migration_files[0]:-}" && -s "${base_files[0]:-}" ]] \
    || dev_fail "필요한 migration 또는 Base Seed SQL 파일이 없습니다."

  if [[ "$scenario_key" == "pm-full-requested-catalog" ]]; then
    required_files+=(
      "$scenario_directory/dev-playground.sql"
      "$scenario_directory/verify-dev-playground.sql"
    )
  fi

  local sql_file
  for sql_file in "${migration_files[@]}" "${base_files[@]}" "${required_files[@]}"; do
    [[ -s "$sql_file" ]] || dev_fail "필요한 reset SQL 파일이 없습니다."
  done
}

write_report() {
  local next_action

  if [[ "$DB_VERIFIED" == true ]]; then
    next_action="DB를 다시 초기화하지 말고 앱 기동·health 문제만 확인하세요."
  elif [[ "$CLEAN_STARTED" == true || "$MARKER_PREEXISTED" == true ]]; then
    next_action="부분 DB에서 앱을 켜지 말고 원인을 수정한 뒤 전체 reset을 처음부터 다시 실행하세요."
  else
    next_action="DB는 변경되지 않았습니다. 입력·권한·대상 설정을 수정한 뒤 다시 실행하세요."
  fi

  umask 077
  {
    echo "### 원격 실행기 결과"
    echo ""
    echo "- 결과: \`$REPORT_RESULT\`"
    echo "- 선택 시나리오: \`$SAFE_SCENARIO\`"
    echo "- 실패/종료 단계: \`$CURRENT_STAGE\`"
    echo "- 마지막 성공 단계: \`$LAST_SUCCESS_STAGE\`"
    echo "- 현재 DB 상태: $DB_STATE"
    echo "- 현재 앱 상태: $APP_STATE"
    echo "- 다음 조치: $next_action"
    echo ""
    echo "> reset 뒤에는 기존 로그인 토큰과 resource ID를 폐기하고 다시 로그인하세요."
  } > "$REPORT_PATH"
  chmod 600 "$REPORT_PATH"
}

inspect_app_state() {
  local raw_state

  raw_state="$(sudo docker inspect --format '{{.State.Status}}' ssing-dev-app 2>/dev/null || true)"
  case "$raw_state" in
    running) APP_STATE="컨테이너 실행 중(health 미확인)" ;;
    exited) APP_STATE="컨테이너 종료" ;;
    paused) APP_STATE="컨테이너 일시정지" ;;
    "") APP_STATE="컨테이너 상태 확인 불가" ;;
    *) APP_STATE="컨테이너 상태 불명확" ;;
  esac
}

on_error() {
  local exit_code=$?
  if [[ "$#" -gt 0 ]]; then
    exit_code="$1"
  fi
  trap - ERR HUP INT TERM

  if [[ "$CLEAN_STARTED" == false ]]; then
    if [[ "$MARKER_CREATED" == true && "$MARKER_PREEXISTED" == false ]]; then
      clear_dev_reset_marker || true
    fi
    if [[ "$MARKER_PREEXISTED" == true ]]; then
      DB_STATE="이전 reset의 부분 또는 미확인 상태"
    else
      DB_STATE="변경 없음"
    fi
  elif [[ "$DB_VERIFIED" == false ]]; then
    DB_STATE="초기화 진행 중 또는 부분 상태"
  else
    DB_STATE="Seed와 migration 검증 완료"
  fi

  if [[ "$APP_STOPPED" == true ]]; then
    APP_STATE="중지 상태"
  elif [[ "$HEALTH_VERIFIED" == true ]]; then
    APP_STATE="정상 실행(health UP)"
  else
    inspect_app_state
  fi

  printf '::error title=dev DB reset 실패::%s 단계에서 실패했습니다. 현재 DB 상태: %s. 현재 앱 상태: %s.\n' \
    "$CURRENT_STAGE" "$DB_STATE" "$APP_STATE" >&2
  if [[ "$DB_VERIFIED" == true ]]; then
    printf '안전 안내: DB를 다시 지우지 말고 앱 또는 health 문제만 복구하세요.\n' >&2
  elif [[ "$CLEAN_STARTED" == true || "$MARKER_PREEXISTED" == true ]]; then
    printf '안전 안내: 이전 또는 현재 reset의 부분 DB일 수 있어 앱을 자동 재기동하지 않습니다. 원인을 수정한 뒤 전체 reset을 다시 실행하세요.\n' >&2
  else
    printf '안전 안내: clean 전 실패이므로 DB는 변경되지 않았습니다.\n' >&2
  fi
  write_report || true
  exit "$exit_code"
}

on_signal() {
  local signal_name="$1"
  local exit_code="$2"
  CURRENT_STAGE="${CURRENT_STAGE} 중 실행 중단(${signal_name})"
  on_error "$exit_code"
}

trap on_error ERR
trap 'on_signal HUP 129' HUP
trap 'on_signal INT 130' INT
trap 'on_signal TERM 143' TERM

confirmation="${1:-}"
git_ref_name="${2:-}"
scenario_key="${3:-}"

[[ "$confirmation" == "--confirm-dev-reset" ]] \
  || dev_fail "초기화 확인값이 올바르지 않습니다." 2
[[ "$git_ref_name" == "main" ]] \
  || dev_fail "main ref에서만 dev DB reset을 실행할 수 있습니다." 2

case "$scenario_key" in
  matching-price-vivaldi|matching-no-candidate-alpensia|matching-multi-request-oak|pm-full-requested-catalog)
    SAFE_SCENARIO="$scenario_key"
    ;;
  *)
    dev_fail "허용된 Seed 시나리오가 아닙니다." 2
    ;;
esac
LAST_SUCCESS_STAGE="입력 검증"

CURRENT_STAGE="대상·권한·UTF-8 사전 검사"
dev_require_command sudo
dev_require_command docker
dev_require_command curl
assert_dev_reset_artifacts "$SAFE_SCENARIO"
assert_dev_target
assert_dev_account_separation
select_dev_migration_account

require_dev_value SSING_RESET_COMMIT_SHA
[[ "$SSING_RESET_COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] \
  || dev_fail "reset commit SHA 형식이 올바르지 않습니다."
[[ -f "$DEPLOY_DIR/.env" && -f "$DEPLOY_DIR/$COMPOSE_FILE" ]] \
  || dev_fail "EC2 배포 파일이 준비되지 않았습니다."

deployed_image_line_count="$(grep -c '^SSING_IMAGE=' "$DEPLOY_DIR/.env" || true)"
[[ "$deployed_image_line_count" == "1" ]] \
  || dev_fail "배포된 앱 이미지 설정을 하나로 확정할 수 없습니다."
deployed_image="$(sed -n 's/^SSING_IMAGE=//p' "$DEPLOY_DIR/.env")"
[[ "$deployed_image" =~ :dev-${SSING_RESET_COMMIT_SHA}(@sha256:[0-9a-f]{64})?$ ]] \
  || dev_fail "reset commit과 마지막 배포 설정의 commit이 다릅니다. 먼저 같은 commit을 dev에 배포하세요."
deployed_datasource_line_count="$(grep -c '^SSING_DATASOURCE_URL=' "$DEPLOY_DIR/.env" || true)"
[[ "$deployed_datasource_line_count" == "1" ]] \
  || dev_fail "배포된 앱 datasource 설정을 하나로 확정할 수 없습니다."
deployed_datasource_url="$(sed -n 's/^SSING_DATASOURCE_URL=//p' "$DEPLOY_DIR/.env")"
[[ "$deployed_datasource_url" == "$SSING_DEV_RUNTIME_DATASOURCE_URL" ]] \
  || dev_fail "실제 배포된 앱 datasource와 reset 대상이 서로 다릅니다."
deployed_username_line_count="$(grep -c '^SSING_DATASOURCE_USERNAME=' "$DEPLOY_DIR/.env" || true)"
[[ "$deployed_username_line_count" == "1" ]] \
  || dev_fail "배포된 앱 DB 사용자 설정을 하나로 확정할 수 없습니다."
deployed_runtime_username="$(sed -n 's/^SSING_DATASOURCE_USERNAME=//p' "$DEPLOY_DIR/.env")"
[[ "$deployed_runtime_username" == "$SSING_DEV_RUNTIME_DB_USERNAME" ]] \
  || dev_fail "실제 배포된 앱 DB 사용자와 runtime 계정 설정이 서로 다릅니다."
deployed_spring_profile="$(sed -n 's/^SPRING_PROFILES_ACTIVE=//p' "$DEPLOY_DIR/.env")"
[[ "$deployed_spring_profile" == "dev" ]] \
  || dev_fail "배포된 앱의 Spring profile이 dev가 아닙니다."
deployed_ddl_auto="$(sed -n 's/^SSING_JPA_DDL_AUTO=//p' "$DEPLOY_DIR/.env")"
[[ "$deployed_ddl_auto" == "validate" ]] \
  || dev_fail "배포된 앱의 JPA schema 정책이 validate가 아닙니다. 먼저 안전한 dev 배포를 완료하세요."
running_image="$(sudo docker inspect --format '{{.Config.Image}}' ssing-dev-app 2>/dev/null || true)"
[[ "$running_image" == "$deployed_image" ]] \
  || dev_fail "실행 중인 앱 이미지와 마지막 배포 설정이 다릅니다. 먼저 dev 배포 상태를 복구하세요."

assert_dev_connection_contract
LAST_SUCCESS_STAGE="대상·권한·UTF-8 사전 검사"

CURRENT_STAGE="안전 marker 생성과 앱 중지"
create_dev_reset_marker
MARKER_CREATED=true
sudo docker compose \
  --env-file "$DEPLOY_DIR/.env" \
  --file "$DEPLOY_DIR/$COMPOSE_FILE" \
  stop app
APP_STOPPED=true
APP_STATE="중지 상태"
LAST_SUCCESS_STAGE="앱 중지"

CURRENT_STAGE="Flyway clean"
CLEAN_STARTED=true
DB_STATE="초기화 진행 중 또는 부분 상태"
run_dev_flyway -cleanDisabled=false clean
LAST_SUCCESS_STAGE="Flyway clean"

CURRENT_STAGE="Flyway migrate"
run_dev_flyway migrate
LAST_SUCCESS_STAGE="Flyway migrate"

CURRENT_STAGE="Base Seed 적용"
apply_dev_sql_directory "$PROJECT_ROOT/db/seed/base"
LAST_SUCCESS_STAGE="Base Seed 적용"

CURRENT_STAGE="Scenario Seed 적용"
run_dev_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$SAFE_SCENARIO/seed.sql"
LAST_SUCCESS_STAGE="Scenario Seed 적용"

CURRENT_STAGE="Scenario와 UTF-8 검증"
run_dev_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$SAFE_SCENARIO/verify.sql"
run_dev_mysql_file "$PROJECT_ROOT/db/seed/verify-utf8.sql"
LAST_SUCCESS_STAGE="Scenario와 UTF-8 검증"

if [[ "$SAFE_SCENARIO" == "pm-full-requested-catalog" ]]; then
  CURRENT_STAGE="PM dev playground 적용과 검증"
  run_dev_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$SAFE_SCENARIO/dev-playground.sql"
  run_dev_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$SAFE_SCENARIO/verify-dev-playground.sql"
  LAST_SUCCESS_STAGE="PM dev playground 적용과 검증"
fi

CURRENT_STAGE="최종 Flyway validate와 연결 검증"
run_dev_flyway validate
assert_dev_connection_contract
DB_VERIFIED=true
DB_STATE="Seed와 migration 검증 완료"
clear_dev_reset_marker
LAST_SUCCESS_STAGE="Flyway와 DB 최종 검증"

CURRENT_STAGE="앱 정상 재기동"
APP_STOPPED=false
APP_STATE="재기동 시도 중(상태 미확인)"
sudo docker compose \
  --env-file "$DEPLOY_DIR/.env" \
  --file "$DEPLOY_DIR/$COMPOSE_FILE" \
  up --detach app
APP_STATE="컨테이너 실행 중(health 미확인)"
LAST_SUCCESS_STAGE="앱 재기동"

CURRENT_STAGE="health 확인"
health_ok=false
for attempt in {1..30}; do
  if curl -fsS --max-time 10 http://127.0.0.1:8080/actuator/health \
      | grep -q '"status":"UP"'; then
    health_ok=true
    break
  fi
  sleep 5
done
[[ "$health_ok" == true ]] || dev_fail "애플리케이션 health가 제한 시간 안에 UP이 되지 않았습니다."
HEALTH_VERIFIED=true
APP_STATE="정상 실행(health UP)"
LAST_SUCCESS_STAGE="health 확인"

CURRENT_STAGE="persona 한글 읽기 smoke"
smoke_persona="대뜸GOAT-성빈-일반강습생"
if [[ "$SAFE_SCENARIO" == "pm-full-requested-catalog" ]]; then
  smoke_persona="냅다레전드-유빈-일반강습생"
fi
persona_response="$(curl -fsS --max-time 10 http://127.0.0.1:8080/dev/auth/personas)"
grep -Fq "$smoke_persona" <<< "$persona_response" \
  || dev_fail "기대 persona를 dev 인증 목록에서 읽지 못했습니다."
grep -Fq '"nickname":"대뜸 GOAT 성빈"' <<< "$persona_response" \
  || dev_fail "dev 인증 목록에서 대뜸GOAT-성빈-일반강습생의 정확한 한글 닉네임을 읽지 못했습니다. Seed 문자셋을 확인하세요."
LAST_SUCCESS_STAGE="persona 한글 읽기 smoke"

CURRENT_STAGE="완료"
REPORT_RESULT="성공"
write_report
printf 'dev DB reset이 완료되었습니다. 앱은 scheduler 기본 설정으로 정상 재기동되었습니다.\n'
printf '기존 로그인 토큰과 resource ID를 폐기하고 다시 로그인한 뒤 QA를 시작하세요.\n'
