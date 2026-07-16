#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/dev-common.sh"

CURRENT_STAGE="입력 검증"
CLEAN_STARTED=false
MARKER_CREATED=false
MARKER_PREEXISTED=false

if [[ -e "$(dev_reset_marker_path)" ]]; then
  MARKER_PREEXISTED=true
fi

on_error() {
  local exit_code="${1:-$?}"
  trap - ERR HUP INT TERM

  if [[ "$CLEAN_STARTED" == false \
      && "$MARKER_CREATED" == true \
      && "$MARKER_PREEXISTED" == false ]]; then
    clear_dev_reset_marker || true
  fi

  printf '::error title=dev 배포 DB 준비 실패::%s 단계에서 실패했습니다.\n' "$CURRENT_STAGE" >&2
  if [[ "$CLEAN_STARTED" == true ]]; then
    printf '안전 안내: 부분 DB일 수 있어 marker를 유지하고 앱 재기동을 차단합니다. 최신 main 배포로 전체 준비를 다시 실행하세요.\n' >&2
  else
    printf '안전 안내: clean 전 실패이므로 DB는 변경되지 않았습니다.\n' >&2
  fi
  exit "$exit_code"
}

on_signal() {
  CURRENT_STAGE="${CURRENT_STAGE} 중 실행 중단(${1})"
  on_error "$2"
}

trap on_error ERR
trap 'on_signal HUP 129' HUP
trap 'on_signal INT 130' INT
trap 'on_signal TERM 143' TERM

operation="${1:-}"
git_ref_name="${2:-}"
deploy_dir="$(dev_deploy_dir)"
compose_file="${SSING_DEV_COMPOSE_FILE:-docker-compose.dev.yml}"
candidate_env="$deploy_dir/.env.next"
preflight_marker="$deploy_dir/.dev-deploy-preflight"

case "$operation" in
  --preflight-dev-deploy|--confirm-dev-deploy-reset)
    ;;
  *)
    dev_fail "배포 사전 검증 또는 초기화 확인값이 올바르지 않습니다." 2
    ;;
esac
[[ "$git_ref_name" == "main" ]] \
  || dev_fail "main ref에서만 dev 배포 DB 초기화를 실행할 수 있습니다." 2
require_dev_value SSING_DEPLOY_COMMIT_SHA
[[ "$SSING_DEPLOY_COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] \
  || dev_fail "배포 commit SHA 형식이 올바르지 않습니다."
[[ "$PROJECT_ROOT" == "$deploy_dir/releases/$SSING_DEPLOY_COMMIT_SHA" ]] \
  || dev_fail "배포 DB 실행기는 해당 commit의 고정 release snapshot에서만 실행할 수 있습니다."

if [[ "$operation" == "--preflight-dev-deploy" ]]; then
  rm -f -- "$preflight_marker" "${preflight_marker}.tmp"
fi

CURRENT_STAGE="대상·권한·후보 설정 사전 검사"
dev_require_command sudo
dev_require_command docker
assert_dev_target
assert_dev_account_separation
select_dev_migration_account

if [[ "$MARKER_PREEXISTED" == true ]]; then
  printf '이전 dev DB 작업의 incomplete marker를 확인했습니다. 최신 main 기준 전체 초기화로 복구합니다.\n'
fi

[[ -s "$candidate_env" && -s "$deploy_dir/$compose_file" ]] \
  || dev_fail "후보 런타임 설정 또는 Compose 파일이 준비되지 않았습니다."
for required_file in \
  "$PROJECT_ROOT/src/main/resources/db/migration"/*.sql \
  "$PROJECT_ROOT/db/seed/base"/*.sql \
  "$PROJECT_ROOT/db/seed/verify-base.sql" \
  "$PROJECT_ROOT/db/seed/verify-utf8.sql"; do
  [[ -s "$required_file" ]] || dev_fail "필요한 migration 또는 base seed 파일이 없습니다."
done

candidate_image_count="$(grep -c '^SSING_IMAGE=' "$candidate_env" || true)"
[[ "$candidate_image_count" == "1" ]] \
  || dev_fail "후보 이미지 설정을 하나로 확정할 수 없습니다."
candidate_image="$(sed -n 's/^SSING_IMAGE=//p' "$candidate_env")"
[[ "$candidate_image" =~ :dev-${SSING_DEPLOY_COMMIT_SHA}@sha256:[0-9a-f]{64}$ ]] \
  || dev_fail "후보 이미지가 배포 commit과 digest로 고정되지 않았습니다."

candidate_datasource="$(sed -n 's/^SSING_DATASOURCE_URL=//p' "$candidate_env")"
candidate_username="$(sed -n 's/^SSING_DATASOURCE_USERNAME=//p' "$candidate_env")"
candidate_profile="$(sed -n 's/^SPRING_PROFILES_ACTIVE=//p' "$candidate_env")"
candidate_ddl_auto="$(sed -n 's/^SSING_JPA_DDL_AUTO=//p' "$candidate_env")"
[[ "$candidate_datasource" == "$SSING_DEV_RUNTIME_DATASOURCE_URL" ]] \
  || dev_fail "후보 앱 datasource와 DB 초기화 대상이 다릅니다."
[[ "$candidate_username" == "$SSING_DEV_RUNTIME_DB_USERNAME" ]] \
  || dev_fail "후보 앱 runtime 계정이 배포 설정과 다릅니다."
[[ "$candidate_profile" == "dev" ]] \
  || dev_fail "후보 앱 profile이 dev가 아닙니다."
[[ "$candidate_ddl_auto" == "validate" ]] \
  || dev_fail "후보 앱 JPA schema 정책이 validate가 아닙니다."
calculate_preflight_fingerprint() {
  {
    printf '%s\0' "$SSING_DEPLOY_COMMIT_SHA"
    command cat "$candidate_env"
    command cat "$deploy_dir/$compose_file"
  } | dev_sha256
}

if [[ "$operation" == "--preflight-dev-deploy" ]]; then
  CURRENT_STAGE="후보 Compose 설정 검증"
  sudo env SSING_RUNTIME_ENV_FILE=.env.next docker compose \
    --env-file "$candidate_env" \
    --file "$deploy_dir/$compose_file" \
    config --quiet

  CURRENT_STAGE="후보 Docker 이미지 pull"
  sudo env SSING_RUNTIME_ENV_FILE=.env.next docker compose \
    --env-file "$candidate_env" \
    --file "$deploy_dir/$compose_file" \
    pull

  CURRENT_STAGE="dev DB 연결과 Flyway 실행기 사전 검증"
  assert_dev_connection_contract
  run_dev_flyway info

  preflight_fingerprint="$(calculate_preflight_fingerprint)"
  printf '%s\n' "$preflight_fingerprint" > "${preflight_marker}.tmp"
  chmod 600 "${preflight_marker}.tmp"
  mv -f "${preflight_marker}.tmp" "$preflight_marker"

  CURRENT_STAGE="완료"
  printf 'dev 배포 사전 검증이 완료되었습니다. 현재 main 재확인 뒤 DB 초기화를 진행할 수 있습니다.\n'
  exit 0
fi

CURRENT_STAGE="사전 검증 증거 확인"
[[ -s "$preflight_marker" ]] \
  || dev_fail "dev 배포 사전 검증이 완료되지 않아 DB 초기화를 차단했습니다."
expected_preflight_fingerprint="$(calculate_preflight_fingerprint)"
actual_preflight_fingerprint="$(< "$preflight_marker")"
if [[ "$actual_preflight_fingerprint" != "$expected_preflight_fingerprint" ]]; then
  rm -f -- "$preflight_marker"
  dev_fail "사전 검증 뒤 후보 설정이 바뀌어 DB 초기화를 차단했습니다."
fi
rm -f -- "$preflight_marker"

CURRENT_STAGE="안전 marker 생성과 앱 중지"
create_dev_reset_marker
MARKER_CREATED=true
sudo env SSING_RUNTIME_ENV_FILE=.env.next docker compose \
  --env-file "$candidate_env" \
  --file "$deploy_dir/$compose_file" \
  stop app

CURRENT_STAGE="Flyway clean"
CLEAN_STARTED=true
run_dev_flyway -cleanDisabled=false clean

CURRENT_STAGE="Flyway migrate"
run_dev_flyway migrate

CURRENT_STAGE="Flyway validate"
run_dev_flyway validate

CURRENT_STAGE="Base Seed 적용"
apply_dev_sql_directory "$PROJECT_ROOT/db/seed/base"

CURRENT_STAGE="Base Seed와 UTF-8 검증"
run_dev_mysql_file "$PROJECT_ROOT/db/seed/verify-base.sql"
run_dev_mysql_file "$PROJECT_ROOT/db/seed/verify-utf8.sql"
assert_dev_connection_contract

CURRENT_STAGE="완료"
clear_dev_reset_marker
printf 'dev 배포 DB 준비가 완료되었습니다. 최신 image 재기동을 진행할 수 있습니다.\n'
