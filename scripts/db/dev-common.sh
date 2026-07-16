#!/usr/bin/env bash

set -euo pipefail

readonly DEV_DB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$DEV_DB_SCRIPT_DIR/common.sh"

readonly DEV_DB_SCHEMA_ALLOWLIST="ssing"
readonly DEV_DB_DEFAULT_DEPLOY_DIR="/home/ubuntu/ssing"
readonly DEV_DB_RESET_MARKER_NAME=".dev-db-reset-incomplete"
# 취소된 원격 DB 작업이 남아 있으면 같은 이름의 다음 작업을 실패시켜 중복 실행을 막는다.
readonly DEV_DB_DOCKER_CONTAINER_NAME="ssing-dev-db-operation"

dev_fail() {
  printf 'dev DB 작업 실패: %s\n' "$1" >&2
  return "${2:-1}"
}

dev_require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || dev_fail "필수 명령 ${1}을 찾을 수 없습니다. 실행 서버의 설치 상태와 PATH를 확인하세요."
}

require_dev_value() {
  local variable_name="$1"
  [[ -n "${!variable_name:-}" ]] || dev_fail "필수 설정 ${variable_name} 값이 없습니다."
}

dev_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

dev_deploy_dir() {
  printf '%s\n' "${SSING_DEV_DEPLOY_DIR:-$DEV_DB_DEFAULT_DEPLOY_DIR}"
}

dev_reset_marker_path() {
  printf '%s/%s\n' "$(dev_deploy_dir)" "$DEV_DB_RESET_MARKER_NAME"
}

assert_dev_target() {
  require_dev_value SSING_DEV_DB_HOST
  require_dev_value SSING_DEV_DB_PORT
  require_dev_value SSING_DEV_DB_NAME
  require_dev_value SSING_DEV_DB_ALLOWED_TARGET_SHA256
  require_dev_value SSING_DEV_RUNTIME_DATASOURCE_URL

  [[ "${SSING_SEED_TARGET_ENV:-}" == "dev" ]] \
    || dev_fail "이 실행기는 SSING_SEED_TARGET_ENV=dev에서만 사용할 수 있습니다."
  [[ "$SSING_DEV_DB_PORT" == "3306" ]] \
    || dev_fail "허용된 dev DB 포트가 아닙니다."
  [[ "$SSING_DEV_DB_NAME" == "$DEV_DB_SCHEMA_ALLOWLIST" ]] \
    || dev_fail "허용된 dev schema가 아닙니다."
  [[ "$SSING_DEV_DB_HOST" =~ ^[A-Za-z0-9.-]+\.rds\.amazonaws\.com$ ]] \
    || dev_fail "dev RDS host 형식이 아닙니다."

  local normalized_target="${SSING_DEV_DB_HOST}:${SSING_DEV_DB_PORT}/${SSING_DEV_DB_NAME}"
  local actual_target_sha256
  actual_target_sha256="$(printf '%s' "$normalized_target" | dev_sha256)"

  [[ "$SSING_DEV_DB_ALLOWED_TARGET_SHA256" =~ ^[0-9a-f]{64}$ ]] \
    || dev_fail "dev DB 대상 fingerprint 형식이 올바르지 않습니다."
  [[ "$actual_target_sha256" == "$SSING_DEV_DB_ALLOWED_TARGET_SHA256" ]] \
    || dev_fail "dev DB 대상 fingerprint가 allowlist와 다릅니다."

  local runtime_prefix="jdbc:mysql://${normalized_target}"
  [[ "$SSING_DEV_RUNTIME_DATASOURCE_URL" == "$runtime_prefix" \
      || "$SSING_DEV_RUNTIME_DATASOURCE_URL" == "$runtime_prefix?"* ]] \
    || dev_fail "앱 datasource와 DB 작업 대상이 서로 다릅니다."

  local lowered_target
  lowered_target="$(printf '%s' "$normalized_target" | tr '[:upper:]' '[:lower:]')"
  [[ "$lowered_target" != *prod* && "$lowered_target" != *production* ]] \
    || dev_fail "production과 유사한 DB 대상은 사용할 수 없습니다."
}

assert_dev_account_separation() {
  require_dev_value SSING_DEV_RUNTIME_DB_USERNAME
  require_dev_value SSING_DEV_DB_MIGRATION_USERNAME
  [[ "$SSING_DEV_RUNTIME_DB_USERNAME" != "$SSING_DEV_DB_MIGRATION_USERNAME" ]] \
    || dev_fail "runtime 계정과 migration 계정은 서로 달라야 합니다."
}

select_dev_migration_account() {
  require_dev_value SSING_DEV_DB_MIGRATION_USERNAME
  require_dev_value SSING_DEV_DB_MIGRATION_PASSWORD
  SSING_DEV_DB_USERNAME="$SSING_DEV_DB_MIGRATION_USERNAME"
  SSING_DEV_DB_PASSWORD="$SSING_DEV_DB_MIGRATION_PASSWORD"

  [[ "$SSING_DEV_DB_USERNAME" =~ ^[A-Za-z0-9_@.-]+$ ]] \
    || dev_fail "DB 사용자 이름 형식이 올바르지 않습니다."
  [[ "$SSING_DEV_DB_PASSWORD" != *$'\n'* && "$SSING_DEV_DB_PASSWORD" != *$'\r'* ]] \
    || dev_fail "DB 비밀번호에는 줄바꿈을 사용할 수 없습니다."
}

write_dev_mysql_defaults_file() {
  local output_file="$1"

  write_mysql_client_defaults_file "$output_file" "$SSING_DEV_DB_PASSWORD"
  {
    printf 'host=%s\n' "$SSING_DEV_DB_HOST"
    printf 'port=%s\n' "$SSING_DEV_DB_PORT"
    printf 'user=%s\n' "$SSING_DEV_DB_USERNAME"
    printf 'database=%s\n' "$SSING_DEV_DB_NAME"
  } >> "$output_file"
}

redact_dev_db_output() {
  local jdbc_prefix="$1"
  local db_host="$2"
  local line

  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ -n "$jdbc_prefix" ]]; then
      line="${line//"$jdbc_prefix"/[REDACTED_DEV_DB_URL]}"
    fi
    if [[ -n "$db_host" ]]; then
      line="${line//"$db_host"/[REDACTED_DEV_DB_HOST]}"
    fi
    printf '%s\n' "$line"
  done
}

# Docker 하위 실행기는 report를 쓰지 않고 원본 종료 코드만 상위 함수에 넘긴다.
_run_dev_mysql_client() (
  set -e
  trap - ERR

  local cleanup_command
  local defaults_file
  local db_host="$SSING_DEV_DB_HOST"
  local jdbc_prefix="jdbc:mysql://${SSING_DEV_DB_HOST}:${SSING_DEV_DB_PORT}/${SSING_DEV_DB_NAME}"
  local -a pipeline_status

  umask 077
  defaults_file="$(mktemp /tmp/ssing-dev-mysql-client.XXXXXX)"
  printf -v cleanup_command 'rm -f -- %q' "$defaults_file"
  trap "$cleanup_command" EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM

  write_dev_mysql_defaults_file "$defaults_file"

  unset MYSQL_PWD \
    SSING_DEV_DB_PASSWORD \
    SSING_DEV_DB_MIGRATION_PASSWORD \
    SSING_DEV_RUNTIME_DATASOURCE_URL

  {
    if sudo docker run --rm --interactive \
        --name "$DEV_DB_DOCKER_CONTAINER_NAME" \
        --mount "type=bind,src=${defaults_file},dst=${MYSQL_CLIENT_DEFAULTS_PATH},readonly" \
        "$MYSQL_IMAGE" \
        mysql \
        "--defaults-extra-file=${MYSQL_CLIENT_DEFAULTS_PATH}" \
        --default-character-set=utf8mb4 \
        "$@" \
        2>&1 1>&3 \
        | redact_dev_db_output "$jdbc_prefix" "$db_host" >&2; then
      pipeline_status=("${PIPESTATUS[@]}")
    else
      pipeline_status=("${PIPESTATUS[@]}")
    fi
  } 3>&1

  if [[ "${pipeline_status[0]}" -ne 0 ]]; then
    return "${pipeline_status[0]}"
  fi
  return "${pipeline_status[1]}"
)

run_dev_mysql_client() {
  local command_status
  local restore_errexit=false
  local saved_err_trap

  # reset/migration의 ERR trap만 한 번 실행되도록 하위 trap을 격리한 뒤 원래 shell 상태를 복구한다.
  saved_err_trap="$(trap -p ERR)"
  trap - ERR
  [[ "$-" == *e* ]] && restore_errexit=true
  set +e
  _run_dev_mysql_client "$@"
  command_status=$?
  if [[ "$restore_errexit" == true ]]; then
    set -e
  fi
  if [[ -n "$saved_err_trap" ]]; then
    eval "$saved_err_trap"
  fi
  return "$command_status"
}

run_dev_mysql_file() {
  local sql_file="$1"
  [[ -f "$sql_file" ]] || dev_fail "필요한 SQL 파일이 없습니다."

  run_dev_mysql_client --show-warnings < "$sql_file"
}

run_dev_mysql_query() {
  local sql="$1"

  run_dev_mysql_client \
    --batch \
    --raw \
    --skip-column-names \
    --execute="$sql"
}

# Flyway stdout·stderr를 함께 정리하되 Docker의 원본 종료 코드는 바꾸지 않는다.
_run_dev_flyway() (
  set -e
  trap - ERR

  local cleanup_command
  local db_host="$SSING_DEV_DB_HOST"
  local flyway_env_file
  local jdbc_prefix="jdbc:mysql://${SSING_DEV_DB_HOST}:${SSING_DEV_DB_PORT}/${SSING_DEV_DB_NAME}"
  local -a pipeline_status

  umask 077
  flyway_env_file="$(mktemp /tmp/ssing-dev-flyway.XXXXXX)"
  printf -v cleanup_command 'rm -f -- %q' "$flyway_env_file"
  trap "$cleanup_command" EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM

  {
    printf 'FLYWAY_URL=jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&permitMysqlScheme=true\n' \
      "$SSING_DEV_DB_HOST" "$SSING_DEV_DB_PORT" "$SSING_DEV_DB_NAME"
    printf 'FLYWAY_USER=%s\n' "$SSING_DEV_DB_USERNAME"
    printf 'FLYWAY_PASSWORD=%s\n' "$SSING_DEV_DB_PASSWORD"
    printf 'FLYWAY_LOCATIONS=filesystem:/flyway/sql\n'
    printf 'FLYWAY_VALIDATE_MIGRATION_NAMING=true\n'
    printf 'FLYWAY_FAIL_ON_MISSING_LOCATIONS=true\n'
    printf 'FLYWAY_VALIDATE_ON_MIGRATE=true\n'
    printf 'FLYWAY_BASELINE_ON_MIGRATE=false\n'
    printf 'FLYWAY_CLEAN_DISABLED=true\n'
  } > "$flyway_env_file"
  chmod 600 "$flyway_env_file"

  unset SSING_DEV_DB_PASSWORD \
    SSING_DEV_DB_MIGRATION_PASSWORD \
    SSING_DEV_RUNTIME_DATASOURCE_URL

  if sudo docker run --rm \
      --name "$DEV_DB_DOCKER_CONTAINER_NAME" \
      --volume "$PROJECT_ROOT/src/main/resources/db/migration:/flyway/sql:ro" \
      --env-file "$flyway_env_file" \
      "$FLYWAY_IMAGE" "$@" \
      2>&1 \
      | redact_dev_db_output "$jdbc_prefix" "$db_host"; then
    pipeline_status=("${PIPESTATUS[@]}")
  else
    pipeline_status=("${PIPESTATUS[@]}")
  fi

  if [[ "${pipeline_status[0]}" -ne 0 ]]; then
    return "${pipeline_status[0]}"
  fi
  return "${pipeline_status[1]}"
)

run_dev_flyway() {
  local command_status
  local restore_errexit=false
  local saved_err_trap

  # reset/migration의 ERR trap만 한 번 실행되도록 하위 trap을 격리한 뒤 원래 shell 상태를 복구한다.
  saved_err_trap="$(trap -p ERR)"
  trap - ERR
  [[ "$-" == *e* ]] && restore_errexit=true
  set +e
  _run_dev_flyway "$@"
  command_status=$?
  if [[ "$restore_errexit" == true ]]; then
    set -e
  fi
  if [[ -n "$saved_err_trap" ]]; then
    eval "$saved_err_trap"
  fi
  return "$command_status"
}

apply_dev_sql_directory() {
  local sql_directory="$1"
  local sql_files=("$sql_directory"/*.sql)
  [[ -e "${sql_files[0]:-}" ]] || dev_fail "적용할 SQL 파일이 없습니다."

  local sql_file
  for sql_file in "${sql_files[@]}"; do
    printf 'Seed SQL 적용: %s\n' "${sql_file#"$PROJECT_ROOT/"}"
    run_dev_mysql_file "$sql_file"
  done
}

assert_dev_connection_contract() {
  local charset_client
  local charset_connection
  local charset_results
  local connection_contract
  local database_contract
  local non_utf8mb4_table_count
  local query_exit_code
  local saved_err_trap

  # 연결·schema·테이블 문자셋을 한 번의 client 실행으로 확인해 원격 reset 대기 시간을 줄인다.
  saved_err_trap="$(trap -p ERR)"
  trap - ERR
  if connection_contract="$(run_dev_mysql_query \
        "SELECT CONCAT(@@character_set_client, '|', @@character_set_connection, '|', @@character_set_results, '|', IF(DATABASE() = '${DEV_DB_SCHEMA_ALLOWLIST}', 'OK', 'WRONG'), '|', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_collation NOT LIKE 'utf8mb4%'));")"; then
    query_exit_code=0
  else
    query_exit_code=$?
  fi
  if [[ -n "$saved_err_trap" ]]; then
    eval "$saved_err_trap"
  fi
  if [[ "$query_exit_code" -ne 0 ]]; then
    return "$query_exit_code"
  fi

  IFS='|' read -r \
    charset_client \
    charset_connection \
    charset_results \
    database_contract \
    non_utf8mb4_table_count <<< "$connection_contract"

  [[ "$charset_client|$charset_connection|$charset_results" == \
      "utf8mb4|utf8mb4|utf8mb4" ]] \
    || dev_fail "MySQL client/connection/results 문자셋이 모두 utf8mb4가 아닙니다."
  [[ "$database_contract" == "OK" ]] \
    || dev_fail "연결된 schema가 dev allowlist와 다릅니다."
  [[ "$non_utf8mb4_table_count" == "0" ]] \
    || dev_fail "dev schema에 utf8mb4가 아닌 테이블이 있습니다."
}

create_dev_reset_marker() {
  local marker_path
  marker_path="$(dev_reset_marker_path)"
  install -d -m 700 "$(dirname "$marker_path")"
  umask 077
  printf 'RESET_INCOMPLETE\n' > "$marker_path"
  chmod 600 "$marker_path"
}

clear_dev_reset_marker() {
  rm -f -- "$(dev_reset_marker_path)"
}

assert_no_incomplete_dev_reset() {
  [[ ! -e "$(dev_reset_marker_path)" ]] \
    || dev_fail "이전 dev DB reset이 완료되지 않아 배포를 중단합니다. reset을 처음부터 다시 완료하세요."
}
