#!/usr/bin/env bash

set -euo pipefail

readonly DB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$DB_SCRIPT_DIR/../.." && pwd)"
readonly FLYWAY_IMAGE="flyway/flyway:12.4.0-alpine"
readonly MYSQL_IMAGE="mysql:8.4.8"
readonly MYSQL_CLIENT_DEFAULTS_PATH="/run/secrets/ssing-mysql-client.cnf"

fail() {
  printf 'seed error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

assert_local_target() {
  [[ "${SSING_SEED_TARGET_ENV:-}" == "local" ]] \
    || fail "only the local seed target is enabled in this slice"
  [[ "${SSING_LOCAL_DB_CONTAINER:-}" == "ssing-local-mysql" ]] \
    || fail "unexpected local database container"
  [[ "${SSING_LOCAL_DB_NAME:-}" == "ssing_local" ]] \
    || fail "unexpected local database name"

  local target="${SSING_LOCAL_DB_CONTAINER} ${SSING_LOCAL_DB_NAME}"
  [[ ! "$target" =~ [Pp][Rr][Oo][Dd] ]] \
    || fail "production-like target is forbidden"
}

assert_scenario_key() {
  local scenario_key="$1"
  [[ "$scenario_key" =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]] \
    || fail "invalid scenario key: $scenario_key"
  [[ -d "$PROJECT_ROOT/db/seed/scenarios/$scenario_key" ]] \
    || fail "unknown scenario key: $scenario_key"
}

flyway_url() {
  local url="jdbc:mysql://127.0.0.1:3306/${SSING_LOCAL_DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
  printf '%s&permitMysqlScheme=true\n' "$url"
}

run_flyway() {
  FLYWAY_PASSWORD="${SSING_LOCAL_DB_ROOT_PASSWORD}" docker run --rm \
    --network "container:${SSING_LOCAL_DB_CONTAINER}" \
    --volume "$PROJECT_ROOT/src/main/resources/db/migration:/flyway/sql:ro" \
    --env "FLYWAY_URL=$(flyway_url)" \
    --env "FLYWAY_USER=root" \
    --env FLYWAY_PASSWORD \
    --env "FLYWAY_LOCATIONS=filesystem:/flyway/sql" \
    --env "FLYWAY_VALIDATE_MIGRATION_NAMING=true" \
    --env "FLYWAY_FAIL_ON_MISSING_LOCATIONS=true" \
    --env "FLYWAY_VALIDATE_ON_MIGRATE=true" \
    --env "FLYWAY_BASELINE_ON_MIGRATE=false" \
    --env "FLYWAY_CLEAN_DISABLED=true" \
    "$FLYWAY_IMAGE" "$@"
}

write_mysql_client_defaults_file() {
  local output_file="$1"
  local password="${2:-${SSING_LOCAL_DB_ROOT_PASSWORD:-}}"

  [[ -n "$password" ]] || fail "database password is required"

  # option file 구문 주입을 막기 위해 개행을 거부하고 따옴표·역슬래시를 escape한다.
  [[ "$password" != *$'\n'* && "$password" != *$'\r'* ]] \
    || fail "local database password must not contain CR or LF"

  password="${password//\\/\\\\}"
  password="${password//\"/\\\"}"
  printf '[client]\npassword="%s"\n' "$password" > "$output_file"
  chmod 600 "$output_file"
}

run_mysql_client() (
  local cleanup_command
  local defaults_file

  umask 077
  defaults_file="$(mktemp /tmp/ssing-mysql-client.XXXXXX)"
  printf -v cleanup_command 'rm -f -- %q' "$defaults_file"
  trap "$cleanup_command" EXIT
  trap 'exit 129' HUP
  trap 'exit 130' INT
  trap 'exit 143' TERM

  write_mysql_client_defaults_file "$defaults_file"

  # MySQL client에는 임시 설정 파일만 전달하고 부모 프로세스의 비밀번호 환경변수는 상속하지 않는다.
  unset MYSQL_PWD SSING_LOCAL_DB_ROOT_PASSWORD
  docker run --rm --interactive \
    --network "container:${SSING_LOCAL_DB_CONTAINER}" \
    --mount "type=bind,src=${defaults_file},dst=${MYSQL_CLIENT_DEFAULTS_PATH},readonly" \
    "$MYSQL_IMAGE" \
    mysql \
    "--defaults-extra-file=${MYSQL_CLIENT_DEFAULTS_PATH}" \
    --default-character-set=utf8mb4 \
    --protocol=TCP \
    --host=127.0.0.1 \
    --port=3306 \
    --user=root \
    --database="${SSING_LOCAL_DB_NAME}" \
    "$@"
)

run_mysql_file() {
  local sql_file="$1"
  [[ -f "$sql_file" ]] || fail "SQL file not found: $sql_file"

  run_mysql_client --show-warnings < "$sql_file"
}

run_mysql_query() {
  local sql="$1"

  run_mysql_client \
    --batch \
    --raw \
    --execute="$sql"
}

apply_sql_directory() {
  local sql_directory="$1"
  local sql_files=("$sql_directory"/*.sql)
  [[ -e "${sql_files[0]:-}" ]] || fail "no SQL files found in: $sql_directory"

  local sql_file
  for sql_file in "${sql_files[@]}"; do
    printf 'Applying %s\n' "${sql_file#"$PROJECT_ROOT/"}"
    run_mysql_file "$sql_file"
  done
}
