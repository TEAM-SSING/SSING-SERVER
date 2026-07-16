#!/usr/bin/env bash

set -euo pipefail

readonly TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$TEST_DIR/../.." && pwd)"
readonly TEST_SHA="0123456789abcdef0123456789abcdef01234567"
readonly TEST_HOST="ssing-dev.cluster-example.ap-northeast-2.rds.amazonaws.com"
readonly TEST_TARGET="${TEST_HOST}:3306/ssing"

fail_test() {
  printf 'dev runner contract test failed: %s\n' "$*" >&2
  exit 1
}

target_sha256() {
  printf '%s' "$1" \
    | { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else shasum -a 256; fi; } \
    | awk '{print $1}'
}

docker() {
  return 0
}

sudo() {
  local arguments=("$@")
  local joined=" ${arguments[*]} "
  printf '%s\n' "$joined" >> "$FAKE_COMMAND_LOG"

  if [[ "$joined" == *" docker inspect "*" {{.Config.Image}} "* ]]; then
    printf '%s\n' "$FAKE_RUNNING_IMAGE"
    return 0
  fi
  if [[ "$joined" == *" docker inspect "*" {{.State.Status}} "* ]]; then
    printf 'running\n'
    return 0
  fi

  if [[ "$joined" == *" docker compose "*" stop app "* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "app-stop" ]] || return 41
    return 0
  fi
  if [[ "$joined" == *" docker compose "*" config --quiet "* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "compose-config" ]] || return 55
    return 0
  fi
  if [[ "$joined" == *" docker compose "*" pull "* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "compose-pull" ]] || return 56
    return 0
  fi
  if [[ "$joined" == *" docker compose "*" up --detach app "* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "app-start" ]] || return 42
    return 0
  fi

  if [[ "${FAKE_FAIL_STAGE:-}" == "docker-name-conflict" \
      && "$joined" == *" docker run "* \
      && "$joined" == *" --name ssing-dev-db-operation "* ]]; then
    return 125
  fi

  if [[ "$joined" == *" flyway/flyway:"* ]]; then
    [[ "$joined" == *" --name ssing-dev-db-operation "* ]] || return 57
    local argument argument_index flyway_env_file=""
    for argument_index in "${!arguments[@]}"; do
      if [[ "${arguments[$argument_index]}" == "--env-file" ]]; then
        flyway_env_file="${arguments[$((argument_index + 1))]:-}"
        break
      fi
    done
    [[ -n "$flyway_env_file" && -f "$flyway_env_file" ]] || return 52
    grep -Fxq 'FLYWAY_USER=ssing_migration' "$flyway_env_file" || return 52

    local flyway_action="${arguments[$((${#arguments[@]} - 1))]}"
    local flyway_should_fail=false
    printf 'Database: jdbc:mysql://%s:%s/%s (MySQL 8.4)\n' \
      "$SSING_DEV_DB_HOST" "$SSING_DEV_DB_PORT" "$SSING_DEV_DB_NAME"
    [[ "${FAKE_FAIL_STAGE:-}" != "flyway-${flyway_action}" ]] \
      || flyway_should_fail=true
    if [[ "$flyway_should_fail" == true ]]; then
      printf 'ERROR: Unable to obtain connection from database (jdbc:mysql://%s:%s/%s)\n' \
        "$SSING_DEV_DB_HOST" "$SSING_DEV_DB_PORT" "$SSING_DEV_DB_NAME" >&2
      return 43
    fi
    return 0
  fi

  if [[ "$joined" == *" mysql "* ]]; then
    [[ "$joined" == *" --name ssing-dev-db-operation "* ]] || return 57
    local argument argument_index mysql_command_index=-1 mysql_defaults_file=""
    for argument_index in "${!arguments[@]}"; do
      if [[ "${arguments[$argument_index]}" == "mysql" ]]; then
        mysql_command_index="$argument_index"
        break
      fi
    done
    [[ "$mysql_command_index" -ge 0 ]] || return 51
    [[ "${arguments[$((mysql_command_index + 1))]:-}" == \
        "--defaults-extra-file=${MYSQL_CLIENT_DEFAULTS_PATH}" ]] || return 51
    [[ "${arguments[$((mysql_command_index + 2))]:-}" == \
        "--default-character-set=utf8mb4" ]] || return 51

    for argument in "${arguments[@]}"; do
      if [[ "$argument" == type=bind,src=*,dst="${MYSQL_CLIENT_DEFAULTS_PATH}",readonly ]]; then
        mysql_defaults_file="${argument#type=bind,src=}"
        mysql_defaults_file="${mysql_defaults_file%%,dst=*}"
        break
      fi
    done
    [[ -n "$mysql_defaults_file" && -f "$mysql_defaults_file" ]] || return 51
    grep -Fxq 'user=ssing_migration' "$mysql_defaults_file" || return 51

    if [[ "${FAKE_FAIL_STAGE:-}" == "mysql-connect" ]]; then
      printf "ERROR 2003 (HY000): Can't connect to MySQL server on '%s:%s'\n" \
        "$SSING_DEV_DB_HOST" "$SSING_DEV_DB_PORT" >&2
      return 50
    fi

    for argument in "${arguments[@]}"; do
      case "$argument" in
        --execute=*character_set_client*)
          local charset_occurrence
          local charset_contract="utf8mb4|utf8mb4|utf8mb4"
          local table_charset_count=0
          charset_occurrence="$(grep -Fc 'character_set_client' "$FAKE_COMMAND_LOG")"
          if [[ "${FAKE_FAIL_STAGE:-}" == "charset" \
              || ( "${FAKE_FAIL_STAGE:-}" == "final-charset" && "$charset_occurrence" -eq 2 ) \
              || ( "${FAKE_FAIL_STAGE:-}" == "migration-final-charset" && "$charset_occurrence" -eq 2 ) ]]; then
            charset_contract="latin1|latin1|latin1"
          fi
          [[ "${FAKE_FAIL_STAGE:-}" != "table-charset" ]] || table_charset_count=1
          printf '%s|OK|%s\n' "$charset_contract" "$table_charset_count"
          return 0
          ;;
        --execute=*"DATABASE() ="*)
          printf 'OK\n'
          return 0
          ;;
        --execute=*information_schema.tables*)
          printf '0\n'
          return 0
          ;;
      esac
    done

    local sql_payload
    sql_payload="$(command cat)"
    if [[ "$sql_payload" == *"INSERT INTO resorts"* ]]; then
      printf ' SQL_STAGE=base\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"Local/CI SNAPSHOT only"* ]]; then
      printf ' SQL_STAGE=scenario\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"base_seed_contract_assertion"* ]]; then
      printf ' SQL_STAGE=base-verify\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"seed_utf8_contract_assertion"* ]]; then
      printf ' SQL_STAGE=utf8\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"seed_contract_assertion"* ]]; then
      printf ' SQL_STAGE=verify\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"Dev QA playground overlay"* ]]; then
      printf ' SQL_STAGE=playground\n' >> "$FAKE_COMMAND_LOG"
    elif [[ "$sql_payload" == *"dev_playground_contract_assertion"* ]]; then
      printf ' SQL_STAGE=playground-verify\n' >> "$FAKE_COMMAND_LOG"
    fi
    case "${FAKE_FAIL_STAGE:-}" in
      base)
        [[ "$sql_payload" != *"INSERT INTO resorts"* ]] || return 44
        ;;
      base-verify)
        [[ "$sql_payload" != *"base_seed_contract_assertion"* ]] || return 54
        ;;
      scenario)
        [[ "$sql_payload" != *"Local/CI SNAPSHOT only"* ]] || return 45
        ;;
      verify)
        [[ "$sql_payload" != *"seed_contract_assertion"* ]] || return 46
        ;;
      utf8)
        [[ "$sql_payload" != *"seed_utf8_contract_assertion"* ]] || return 47
        ;;
      playground)
        [[ "$sql_payload" != *"Dev QA playground overlay"* ]] || return 48
        ;;
      playground-verify)
        [[ "$sql_payload" != *"dev_playground_contract_assertion"* ]] || return 49
        ;;
    esac
    return 0
  fi

  return 0
}

curl() {
  local joined=" $* "
  printf ' curl%s\n' "$joined" >> "$FAKE_COMMAND_LOG"
  if [[ "$joined" == *"/actuator/health"* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "health" ]] || return 22
    printf '{"status":"UP"}\n'
    return 0
  fi
  if [[ "$joined" == *"/dev/auth/personas"* ]]; then
    [[ "${FAKE_FAIL_STAGE:-}" != "persona" ]] || return 22
    if [[ "${FAKE_FAIL_STAGE:-}" == "persona-encoding" ]]; then
      printf '{"data":{"personas":[{"personaKey":"냅다레전드-유빈-일반강습생","nickname":"냅다 레전드 유빈"},{"personaKey":"대뜸GOAT-성빈-일반강습생","nickname":"ëë¸ GOAT ì±ë¹"}]}}\n'
    else
      printf '{"data":{"personas":[{"personaKey":"냅다레전드-유빈-일반강습생","nickname":"냅다 레전드 유빈"},{"personaKey":"대뜸GOAT-성빈-일반강습생","nickname":"대뜸 GOAT 성빈"}]}}\n'
    fi
    return 0
  fi
  return 22
}

sleep() {
  return 0
}

export -f docker sudo curl sleep

for signal_name in HUP INT TERM; do
  grep -Fq "trap 'on_signal ${signal_name} " "$PROJECT_ROOT/scripts/db/reset-dev.sh" \
    || fail_test "${signal_name} 중단 시 한글 실패 report를 남기는 trap이 없습니다."
done

grep -Fq 'readonly DEV_DB_DOCKER_CONTAINER_NAME="ssing-dev-db-operation"' \
  "$PROJECT_ROOT/scripts/db/dev-common.sh" \
  || fail_test "dev DB Docker 작업의 고정 컨테이너 이름이 없습니다."

assert_no_mutation_command() {
  local command_log="$1"
  ! grep -Eq ' compose .* (stop|up) | flyway/flyway:|SQL_STAGE=' "$command_log" \
    || fail_test "clean 전 실패가 mutation 명령을 실행했습니다."
}

assert_no_destructive_deploy_command() {
  local command_log="$1"
  ! grep -Eq \
    ' compose .* (stop|up) | flyway/flyway:.* (clean|migrate|validate)[[:space:]]*$|SQL_STAGE=' \
    "$command_log" \
    || fail_test "배포 사전 검증 실패가 앱 또는 DB를 변경했습니다."
}

assert_secret_safe() {
  local output_file="$1"
  local report_file="$2"
  local command_log="$3"

  for secret_text in \
    "migration-secret#42" \
    "$SSING_DEV_DB_HOST" \
    "jdbc:mysql://"; do
    ! grep -Fq "$secret_text" "$output_file" "$report_file" "$command_log" 2>/dev/null \
      || fail_test "민감한 대상 또는 credential이 로그에 노출됐습니다."
  done
}

command_position() {
  local command_log="$1"
  local pattern="$2"
  local occurrence="${3:-1}"

  grep -nE "$pattern" "$command_log" \
    | sed -n "${occurrence}p" \
    | cut -d: -f1
}

assert_pm_success_order() {
  local command_log="$1"
  local previous_position=0
  local label pattern occurrence position
  while IFS='|' read -r label pattern occurrence; do
    position="$(command_position "$command_log" "$pattern" "$occurrence")"
    [[ -n "$position" ]] || fail_test "PM 성공 순서에서 $label 단계가 없습니다."
    [[ "$position" -gt "$previous_position" ]] \
      || fail_test "PM 성공 순서가 잘못됐습니다: $label"
    previous_position="$position"
  done <<'EOF'
app-stop| compose .* stop app |1
flyway-clean| flyway/flyway:.* clean[[:space:]]*$|1
migrate| flyway/flyway:.* migrate[[:space:]]*$|1
base|SQL_STAGE=base|1
scenario|SQL_STAGE=scenario|1
scenario-verify|SQL_STAGE=verify|1
utf8-verify|SQL_STAGE=utf8|1
playground|SQL_STAGE=playground$|1
playground-verify|SQL_STAGE=playground-verify|1
final-validate| flyway/flyway:.* validate[[:space:]]*$|1
app-start| compose .* up --detach app |1
health|/actuator/health|1
persona|/dev/auth/personas|1
EOF
}

assert_deploy_prepare_success_order() {
  local command_log="$1"
  local previous_position=0
  local label pattern occurrence position
  while IFS='|' read -r label pattern occurrence; do
    position="$(command_position "$command_log" "$pattern" "$occurrence")"
    [[ -n "$position" ]] || fail_test "자동 배포 DB 준비 순서에서 $label 단계가 없습니다."
    [[ "$position" -gt "$previous_position" ]] \
      || fail_test "자동 배포 DB 준비 순서가 잘못됐습니다: $label"
    previous_position="$position"
  done <<'EOF'
compose-config| compose .* config --quiet |1
compose-pull| compose .* pull |1
flyway-info| flyway/flyway:.* info[[:space:]]*$|1
app-stop| compose .* stop app |1
flyway-clean| flyway/flyway:.* clean[[:space:]]*$|1
migrate| flyway/flyway:.* migrate[[:space:]]*$|1
validate| flyway/flyway:.* validate[[:space:]]*$|1
base|SQL_STAGE=base|1
base-verify|SQL_STAGE=base-verify|1
utf8-verify|SQL_STAGE=utf8|1
EOF
}

assert_deploy_preflight_success_order() {
  local command_log="$1"
  local previous_position=0
  local label pattern occurrence position
  while IFS='|' read -r label pattern occurrence; do
    position="$(command_position "$command_log" "$pattern" "$occurrence")"
    [[ -n "$position" ]] || fail_test "자동 배포 사전 검증 순서에서 $label 단계가 없습니다."
    [[ "$position" -gt "$previous_position" ]] \
      || fail_test "자동 배포 사전 검증 순서가 잘못됐습니다: $label"
    previous_position="$position"
  done <<'EOF'
compose-config| compose .* config --quiet |1
compose-pull| compose .* pull |1
flyway-info| flyway/flyway:.* info[[:space:]]*$|1
EOF
}

run_case() {
  local case_name="$1"
  local fail_stage="$2"
  local confirmation="$3"
  local ref_name="$4"
  local scenario="$5"
  local expected_success="$6"
  local target_hash_override="${7:-}"
  local preexisting_marker="${8:-false}"
  local deployed_datasource_override="${9:-}"
  local runtime_username="${10:-ssing_runtime}"
  local deployed_username_override="${11:-}"
  local deployed_profile="${12:-dev}"
  local deployed_ddl_auto="${13:-validate}"
  local db_host="${RUN_CASE_DB_HOST:-$TEST_HOST}"
  local db_port="${RUN_CASE_DB_PORT:-3306}"
  local db_name="${RUN_CASE_DB_NAME:-ssing}"
  local case_target="${db_host}:${db_port}/${db_name}"
  local deployed_image="${RUN_CASE_DEPLOYED_IMAGE:-$EXPECTED_IMAGE}"
  local running_image="${RUN_CASE_RUNNING_IMAGE:-$deployed_image}"
  local reset_script="${RUN_CASE_RESET_SCRIPT:-$PROJECT_ROOT/scripts/db/reset-dev.sh}"

  local case_dir="$TEST_TMP/$case_name"
  local deploy_dir="$case_dir/deploy"
  local output_file="$case_dir/output.log"
  local report_file="$case_dir/report.md"
  local command_log="$case_dir/commands.log"
  mkdir -p "$deploy_dir"
  : > "$command_log"
  {
    printf 'SSING_IMAGE=%s\n' "$deployed_image"
    printf 'SSING_DATASOURCE_URL=%s\n' \
      "${deployed_datasource_override:-jdbc:mysql://${case_target}?useUnicode=true}"
    printf 'SSING_DATASOURCE_USERNAME=%s\n' \
      "${deployed_username_override:-$runtime_username}"
    printf 'SPRING_PROFILES_ACTIVE=%s\n' "$deployed_profile"
    printf 'SSING_JPA_DDL_AUTO=%s\n' "$deployed_ddl_auto"
  } > "$deploy_dir/.env"
  if [[ -n "${RUN_CASE_EXTRA_ENV_LINES:-}" ]]; then
    printf '%s\n' "$RUN_CASE_EXTRA_ENV_LINES" >> "$deploy_dir/.env"
  fi
  printf 'services: {}\n' > "$deploy_dir/docker-compose.dev.yml"
  if [[ "$preexisting_marker" == true ]]; then
    printf 'RESET_INCOMPLETE\n' > "$deploy_dir/.dev-db-reset-incomplete"
  fi

  export FAKE_COMMAND_LOG="$command_log"
  export FAKE_FAIL_STAGE="$fail_stage"
  export FAKE_RUNNING_IMAGE="$running_image"
  export SSING_SEED_TARGET_ENV="dev"
  export SSING_DEV_DEPLOY_DIR="$deploy_dir"
  export SSING_DEV_COMPOSE_FILE="docker-compose.dev.yml"
  export SSING_DEV_RESET_REPORT_PATH="$report_file"
  export SSING_DEV_DB_HOST="$db_host"
  export SSING_DEV_DB_PORT="$db_port"
  export SSING_DEV_DB_NAME="$db_name"
  export SSING_DEV_DB_ALLOWED_TARGET_SHA256="${target_hash_override:-$TEST_TARGET_SHA256}"
  export SSING_DEV_RUNTIME_DATASOURCE_URL="jdbc:mysql://${case_target}?useUnicode=true"
  export SSING_DEV_RUNTIME_DB_USERNAME="$runtime_username"
  export SSING_DEV_DB_MIGRATION_USERNAME="ssing_migration"
  export SSING_DEV_DB_MIGRATION_PASSWORD="migration-secret#42"
  export SSING_RESET_COMMIT_SHA="$TEST_SHA"

  set +e
  if [[ "${RUN_CASE_MISSING_COMMAND:-}" == "docker" ]]; then
    local isolated_bin="$case_dir/isolated-bin"
    mkdir -p "$isolated_bin"
    ln -s "$(command -v dirname)" "$isolated_bin/dirname"
    ln -s "$(command -v chmod)" "$isolated_bin/chmod"
    /usr/bin/env -u 'BASH_FUNC_docker%%' PATH="$isolated_bin" /bin/bash \
      "$reset_script" \
      "$confirmation" "$ref_name" "$scenario" > "$output_file" 2>&1
  else
    bash "$reset_script" \
      "$confirmation" "$ref_name" "$scenario" > "$output_file" 2>&1
  fi
  local exit_code=$?
  set -e
  printf '%s\n' "$exit_code" > "$case_dir/exit-code"

  if [[ "$expected_success" == true ]]; then
    [[ "$exit_code" -eq 0 ]] || fail_test "$case_name 성공 계약이 실패했습니다: exit=$exit_code"
    grep -Fq '결과: `성공`' "$report_file" \
      || fail_test "$case_name 성공 report가 없습니다."
    [[ ! -e "$deploy_dir/.dev-db-reset-incomplete" ]] \
      || fail_test "$case_name 성공 뒤 incomplete marker가 남았습니다."
    grep -Eq ' compose .* stop app ' "$command_log" \
      || fail_test "$case_name 앱 중지 명령이 없습니다."
    grep -Eq ' flyway/flyway:.* clean ' "$command_log" \
      || fail_test "$case_name Flyway clean 명령이 없습니다."
    grep -Eq ' compose .* up --detach app ' "$command_log" \
      || fail_test "$case_name 앱 재기동 명령이 없습니다."
    ! grep -Fq 'SSING_SCHEDULED_JOBS_ENABLED' "$command_log" \
      || fail_test "$case_name scheduler 설정을 덮어썼습니다."
  else
    [[ "$exit_code" -ne 0 ]] || fail_test "$case_name 실패 계약이 성공했습니다."
  fi

  assert_secret_safe "$output_file" "$report_file" "$command_log"
}

run_migrate_case() {
  local case_name="$1"
  local fail_stage="$2"
  local marker_present="$3"
  local expected_success="$4"
  local runtime_username="${5:-ssing_runtime}"
  local case_dir="$TEST_TMP/$case_name"
  local deploy_dir="$case_dir/deploy"
  local output_file="$case_dir/output.log"
  local command_log="$case_dir/commands.log"
  mkdir -p "$deploy_dir"
  : > "$command_log"
  if [[ "$marker_present" == true ]]; then
    printf 'RESET_INCOMPLETE\n' > "$deploy_dir/.dev-db-reset-incomplete"
  fi

  export FAKE_COMMAND_LOG="$command_log"
  export FAKE_FAIL_STAGE="$fail_stage"
  export SSING_SEED_TARGET_ENV="dev"
  export SSING_DEV_DEPLOY_DIR="$deploy_dir"
  export SSING_DEV_DB_HOST="$TEST_HOST"
  export SSING_DEV_DB_PORT="3306"
  export SSING_DEV_DB_NAME="ssing"
  export SSING_DEV_DB_ALLOWED_TARGET_SHA256="$TEST_TARGET_SHA256"
  export SSING_DEV_RUNTIME_DATASOURCE_URL="jdbc:mysql://${TEST_TARGET}?useUnicode=true"
  export SSING_DEV_RUNTIME_DB_USERNAME="$runtime_username"
  export SSING_DEV_DB_MIGRATION_USERNAME="ssing_migration"
  export SSING_DEV_DB_MIGRATION_PASSWORD="migration-secret#42"

  set +e
  bash "$PROJECT_ROOT/scripts/db/migrate-dev.sh" > "$output_file" 2>&1
  local exit_code=$?
  set -e
  printf '%s\n' "$exit_code" > "$case_dir/exit-code"

  if [[ "$expected_success" == true ]]; then
    [[ "$exit_code" -eq 0 ]] || fail_test "$case_name migration 성공 계약이 실패했습니다."
    grep -Eq ' flyway/flyway:.* migrate ' "$command_log" \
      || fail_test "$case_name migrate 명령이 없습니다."
    grep -Eq ' flyway/flyway:.* validate ' "$command_log" \
      || fail_test "$case_name validate 명령이 없습니다."
    ! grep -Eq ' flyway/flyway:.* clean ' "$command_log" \
      || fail_test "$case_name 일반 배포 migration이 clean을 실행했습니다."
  else
    [[ "$exit_code" -ne 0 ]] || fail_test "$case_name migration 실패 계약이 성공했습니다."
  fi

  assert_secret_safe "$output_file" "$case_dir/missing-report.md" "$command_log"
}

run_deploy_prepare_case() {
  local case_name="$1"
  local fail_stage="$2"
  local marker_present="$3"
  local expected_success="$4"
  local confirmation="${5:---confirm-dev-deploy-reset}"
  local ref_name="${6:-main}"
  local candidate_image="${7:-$EXPECTED_DIGEST_IMAGE}"
  local current_env_present="${8:-true}"
  local run_preflight_first="${9:-true}"
  local mutate_after_preflight="${10:-false}"
  local case_dir="$TEST_TMP/$case_name"
  local deploy_dir="$case_dir/deploy"
  local release_dir="$deploy_dir/releases/$TEST_SHA"
  local release_script="$release_dir/scripts/db/prepare-dev-deploy-db.sh"
  local output_file="$case_dir/output.log"
  local command_log="$case_dir/commands.log"
  mkdir -p \
    "$release_dir/scripts/db" \
    "$release_dir/src/main/resources/db/migration" \
    "$release_dir/db/seed/base"
  cp \
    "$PROJECT_ROOT/scripts/db/common.sh" \
    "$PROJECT_ROOT/scripts/db/dev-common.sh" \
    "$PROJECT_ROOT/scripts/db/prepare-dev-deploy-db.sh" \
    "$release_dir/scripts/db/"
  cp -R \
    "$PROJECT_ROOT/src/main/resources/db/migration/." \
    "$release_dir/src/main/resources/db/migration/"
  cp -R \
    "$PROJECT_ROOT/db/seed/base/." \
    "$release_dir/db/seed/base/"
  cp \
    "$PROJECT_ROOT/db/seed/verify-base.sql" \
    "$PROJECT_ROOT/db/seed/verify-utf8.sql" \
    "$release_dir/db/seed/"
  : > "$command_log"
  printf 'services: {}\n' > "$deploy_dir/docker-compose.dev.yml"
  if [[ "$current_env_present" == true ]]; then
    {
      printf 'SSING_IMAGE=%s\n' "$EXPECTED_DIGEST_IMAGE"
      printf 'SSING_DATASOURCE_URL=jdbc:mysql://%s?useUnicode=true\n' "$TEST_TARGET"
      printf 'SSING_DATASOURCE_USERNAME=ssing_runtime\n'
      printf 'SPRING_PROFILES_ACTIVE=dev\n'
      printf 'SSING_JPA_DDL_AUTO=validate\n'
    } > "$deploy_dir/.env"
  fi
  {
    printf 'SSING_IMAGE=%s\n' "$candidate_image"
    printf 'SSING_DATASOURCE_URL=jdbc:mysql://%s?useUnicode=true\n' "$TEST_TARGET"
    printf 'SSING_DATASOURCE_USERNAME=ssing_runtime\n'
    printf 'SPRING_PROFILES_ACTIVE=dev\n'
    printf 'SSING_JPA_DDL_AUTO=validate\n'
  } > "$deploy_dir/.env.next"
  if [[ "$marker_present" == true ]]; then
    printf 'RESET_INCOMPLETE\n' > "$deploy_dir/.dev-db-reset-incomplete"
  fi

  export FAKE_COMMAND_LOG="$command_log"
  export FAKE_FAIL_STAGE="$fail_stage"
  export FAKE_RUNNING_IMAGE="$EXPECTED_DIGEST_IMAGE"
  export SSING_SEED_TARGET_ENV="dev"
  export SSING_DEV_DEPLOY_DIR="$deploy_dir"
  export SSING_DEV_COMPOSE_FILE="docker-compose.dev.yml"
  export SSING_DEV_DB_HOST="$TEST_HOST"
  export SSING_DEV_DB_PORT="3306"
  export SSING_DEV_DB_NAME="ssing"
  export SSING_DEV_DB_ALLOWED_TARGET_SHA256="$TEST_TARGET_SHA256"
  export SSING_DEV_RUNTIME_DATASOURCE_URL="jdbc:mysql://${TEST_TARGET}?useUnicode=true"
  export SSING_DEV_RUNTIME_DB_USERNAME="ssing_runtime"
  export SSING_DEV_DB_MIGRATION_USERNAME="ssing_migration"
  export SSING_DEV_DB_MIGRATION_PASSWORD="migration-secret#42"
  export SSING_DEPLOY_COMMIT_SHA="$TEST_SHA"

  set +e
  local exit_code
  if [[ "$confirmation" == "--preflight-dev-deploy" ]]; then
    bash "$release_script" \
      "$confirmation" "$ref_name" > "$output_file" 2>&1
    exit_code=$?
  else
    if [[ "$run_preflight_first" == true ]]; then
      FAKE_FAIL_STAGE="" bash "$release_script" \
        --preflight-dev-deploy main > "$output_file" 2>&1
      local preflight_exit_code=$?
      if [[ "$preflight_exit_code" -ne 0 ]]; then
        set -e
        fail_test "$case_name 자동 배포 사전 검증 준비가 실패했습니다: exit=$preflight_exit_code"
      fi
      if [[ "$mutate_after_preflight" == true ]]; then
        printf 'JAVA_OPTS=-XX:MaxRAMPercentage=70\n' >> "$deploy_dir/.env.next"
      fi
    fi
    FAKE_FAIL_STAGE="$fail_stage" bash "$release_script" \
      "$confirmation" "$ref_name" >> "$output_file" 2>&1
    exit_code=$?
  fi
  set -e
  printf '%s\n' "$exit_code" > "$case_dir/exit-code"

  if [[ "$expected_success" == true ]]; then
    [[ "$exit_code" -eq 0 ]] \
      || fail_test "$case_name 자동 배포 DB 준비 성공 계약이 실패했습니다."
    if [[ "$confirmation" == "--preflight-dev-deploy" ]]; then
      assert_deploy_preflight_success_order "$command_log"
      assert_no_destructive_deploy_command "$command_log"
      [[ ! -e "$deploy_dir/.dev-db-reset-incomplete" ]] \
        || fail_test "$case_name 사전 검증이 reset incomplete marker를 만들었습니다."
      [[ -s "$deploy_dir/.dev-deploy-preflight" ]] \
        || fail_test "$case_name 사전 검증 완료 증거가 없습니다."
    else
      [[ ! -e "$deploy_dir/.dev-db-reset-incomplete" ]] \
        || fail_test "$case_name 성공 뒤 incomplete marker가 남았습니다."
      [[ ! -e "$deploy_dir/.dev-deploy-preflight" ]] \
        || fail_test "$case_name 파괴 작업 뒤 사전 검증 증거가 남았습니다."
      assert_deploy_prepare_success_order "$command_log"
      ! grep -Eq ' compose .* up ' "$command_log" \
        || fail_test "$case_name DB 준비 실행기가 앱을 직접 재기동했습니다."
    fi
  else
    [[ "$exit_code" -ne 0 ]] \
      || fail_test "$case_name 자동 배포 DB 준비 실패 계약이 성공했습니다."
    if [[ "$confirmation" == "--preflight-dev-deploy" ]]; then
      assert_no_destructive_deploy_command "$command_log"
      [[ ! -e "$deploy_dir/.dev-db-reset-incomplete" ]] \
        || fail_test "$case_name 실패한 사전 검증이 reset incomplete marker를 만들었습니다."
      [[ ! -e "$deploy_dir/.dev-deploy-preflight" ]] \
        || fail_test "$case_name 실패한 사전 검증이 완료 증거를 남겼습니다."
    fi
  fi

  assert_secret_safe "$output_file" "$case_dir/missing-report.md" "$command_log"
}

TEST_TMP="$(mktemp -d /tmp/ssing-dev-runner-test.XXXXXX)"
cleanup_test_tmp() {
  if [[ "${KEEP_SSING_DEV_RUNNER_TEST_TMP:-false}" == true ]]; then
    printf 'dev runner test fixtures kept at %s\n' "$TEST_TMP" >&2
  else
    rm -rf -- "$TEST_TMP"
  fi
}
trap cleanup_test_tmp EXIT
EXPECTED_IMAGE="teamssing/server:dev-${TEST_SHA}"
EXPECTED_DIGEST_IMAGE="${EXPECTED_IMAGE}@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
TEST_TARGET_SHA256="$(target_sha256 "$TEST_TARGET")"
export TEST_TMP EXPECTED_IMAGE EXPECTED_DIGEST_IMAGE TEST_TARGET_SHA256

missing_artifact_root="$TEST_TMP/missing-artifact-project"
mkdir -p \
  "$missing_artifact_root/scripts/db" \
  "$missing_artifact_root/src/main/resources/db/migration" \
  "$missing_artifact_root/db/seed/base" \
  "$missing_artifact_root/db/seed/scenarios/matching-price-vivaldi"
cp \
  "$PROJECT_ROOT/scripts/db/common.sh" \
  "$PROJECT_ROOT/scripts/db/dev-common.sh" \
  "$PROJECT_ROOT/scripts/db/reset-dev.sh" \
  "$missing_artifact_root/scripts/db/"
printf '%s\n' '-- migration fixture' 'SELECT 1;' \
  > "$missing_artifact_root/src/main/resources/db/migration/V1__fixture.sql"
printf '%s\n' '-- base fixture' 'SELECT 1;' \
  > "$missing_artifact_root/db/seed/base/001_fixture.sql"
printf '%s\n' '-- scenario fixture' 'SELECT 1;' \
  > "$missing_artifact_root/db/seed/scenarios/matching-price-vivaldi/seed.sql"
printf '%s\n' '-- utf8 fixture' 'SELECT 1;' \
  > "$missing_artifact_root/db/seed/verify-utf8.sql"

run_case invalid-confirm "" wrong-confirm main pm-full-requested-catalog false
assert_no_mutation_command "$TEST_TMP/invalid-confirm/commands.log"

run_case preexisting-marker-preserved "" wrong-confirm main \
  pm-full-requested-catalog false "" true
assert_no_mutation_command "$TEST_TMP/preexisting-marker-preserved/commands.log"
[[ -e "$TEST_TMP/preexisting-marker-preserved/deploy/.dev-db-reset-incomplete" ]] \
  || fail_test "이전 reset의 incomplete marker를 새 실행의 입력 실패가 삭제했습니다."
grep -Fq '이전 reset의 부분 또는 미확인 상태' \
  "$TEST_TMP/preexisting-marker-preserved/report.md" \
  || fail_test "이전 incomplete marker가 있을 때 부분 DB 복구 안내가 없습니다."
! grep -Fq 'DB는 변경되지 않았습니다' \
  "$TEST_TMP/preexisting-marker-preserved/output.log" \
  "$TEST_TMP/preexisting-marker-preserved/report.md" \
  || fail_test "이전 incomplete marker가 있는데 DB 무변경으로 잘못 안내했습니다."

run_case invalid-ref "" --confirm-dev-reset feature pm-full-requested-catalog false
assert_no_mutation_command "$TEST_TMP/invalid-ref/commands.log"

run_case invalid-scenario "" --confirm-dev-reset main not-allowed false
assert_no_mutation_command "$TEST_TMP/invalid-scenario/commands.log"

(
  RUN_CASE_MISSING_COMMAND=docker \
    run_case missing-docker "" --confirm-dev-reset main pm-full-requested-catalog false
)
assert_no_mutation_command "$TEST_TMP/missing-docker/commands.log"
grep -Fq '필수 명령 docker' "$TEST_TMP/missing-docker/output.log" \
  || fail_test "docker 누락 안내가 한글로 제공되지 않았습니다."
grep -Fq '현재 DB 상태: 변경 없음' "$TEST_TMP/missing-docker/report.md" \
  || fail_test "docker 누락 시 DB 무변경 report가 없습니다."

(
  RUN_CASE_RESET_SCRIPT="$missing_artifact_root/scripts/db/reset-dev.sh" \
    run_case missing-scenario-verify "" --confirm-dev-reset main \
      matching-price-vivaldi false
)
assert_no_mutation_command "$TEST_TMP/missing-scenario-verify/commands.log"
grep -Fq '필요한 reset SQL 파일이 없습니다' \
  "$TEST_TMP/missing-scenario-verify/output.log" \
  || fail_test "scenario verify 누락을 clean 전에 안내하지 않았습니다."

run_case invalid-target "" --confirm-dev-reset main pm-full-requested-catalog false \
  "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
assert_no_mutation_command "$TEST_TMP/invalid-target/commands.log"

run_case deployed-target-mismatch "" --confirm-dev-reset main \
  pm-full-requested-catalog false "" false \
  "jdbc:mysql://other-dev.example:3306/ssing"
assert_no_mutation_command "$TEST_TMP/deployed-target-mismatch/commands.log"

run_case account-separation "" --confirm-dev-reset main \
  pm-full-requested-catalog false "" false "" ssing_migration
assert_no_mutation_command "$TEST_TMP/account-separation/commands.log"

run_case deployed-username-mismatch "" --confirm-dev-reset main \
  pm-full-requested-catalog false "" false "" ssing_runtime stale_runtime
assert_no_mutation_command "$TEST_TMP/deployed-username-mismatch/commands.log"

run_case deployed-profile-mismatch "" --confirm-dev-reset main \
  pm-full-requested-catalog false "" false "" ssing_runtime "" prod
assert_no_mutation_command "$TEST_TMP/deployed-profile-mismatch/commands.log"

run_case deployed-ddl-auto-mismatch "" --confirm-dev-reset main \
  pm-full-requested-catalog false "" false "" ssing_runtime "" dev update
assert_no_mutation_command "$TEST_TMP/deployed-ddl-auto-mismatch/commands.log"

stale_image="teamssing/server:dev-1111111111111111111111111111111111111111"
(
  RUN_CASE_DEPLOYED_IMAGE="$stale_image" \
  RUN_CASE_RUNNING_IMAGE="$stale_image" \
    run_case deployed-commit-mismatch "" --confirm-dev-reset main \
      pm-full-requested-catalog false
)
assert_no_mutation_command "$TEST_TMP/deployed-commit-mismatch/commands.log"

(
  RUN_CASE_EXTRA_ENV_LINES="SSING_IMAGE=$stale_image" \
    run_case duplicate-deployed-image "" --confirm-dev-reset main \
      pm-full-requested-catalog false
)
assert_no_mutation_command "$TEST_TMP/duplicate-deployed-image/commands.log"
grep -Fq '배포된 앱 이미지 설정을 하나로 확정할 수 없습니다' \
  "$TEST_TMP/duplicate-deployed-image/output.log" \
  || fail_test "중복 SSING_IMAGE를 clean 전에 차단하지 않았습니다."

(
  RUN_CASE_RUNNING_IMAGE="$stale_image" \
    run_case running-image-mismatch "" --confirm-dev-reset main \
      pm-full-requested-catalog false
)
assert_no_mutation_command "$TEST_TMP/running-image-mismatch/commands.log"

prod_like_target="ssing-prod.cluster-example.ap-northeast-2.rds.amazonaws.com:3306/ssing"
(
  RUN_CASE_DB_HOST="${prod_like_target%%:*}" \
    run_case prod-like-target "" --confirm-dev-reset main \
      pm-full-requested-catalog false "$(target_sha256 "$prod_like_target")"
)
assert_no_mutation_command "$TEST_TMP/prod-like-target/commands.log"

wrong_schema_target="${TEST_HOST}:3306/ssing_other"
(
  RUN_CASE_DB_NAME=ssing_other \
    run_case wrong-schema-target "" --confirm-dev-reset main \
      pm-full-requested-catalog false "$(target_sha256 "$wrong_schema_target")"
)
assert_no_mutation_command "$TEST_TMP/wrong-schema-target/commands.log"

wrong_port_target="${TEST_HOST}:3307/ssing"
(
  RUN_CASE_DB_PORT=3307 \
    run_case wrong-port-target "" --confirm-dev-reset main \
      pm-full-requested-catalog false "$(target_sha256 "$wrong_port_target")"
)
assert_no_mutation_command "$TEST_TMP/wrong-port-target/commands.log"

run_case invalid-charset charset --confirm-dev-reset main pm-full-requested-catalog false
assert_no_mutation_command "$TEST_TMP/invalid-charset/commands.log"

run_case invalid-table-charset table-charset \
  --confirm-dev-reset main pm-full-requested-catalog false
assert_no_mutation_command "$TEST_TMP/invalid-table-charset/commands.log"
grep -Fq 'dev schema에 utf8mb4가 아닌 테이블이 있습니다' \
  "$TEST_TMP/invalid-table-charset/output.log" \
  || fail_test "테이블 문자셋 위반을 clean 전에 차단하지 않았습니다."

run_case failure-mysql-connect mysql-connect \
  --confirm-dev-reset main pm-full-requested-catalog false
assert_no_mutation_command "$TEST_TMP/failure-mysql-connect/commands.log"
[[ "$(< "$TEST_TMP/failure-mysql-connect/exit-code")" == "50" ]] \
  || fail_test "MySQL 연결 실패 종료 코드가 보존되지 않았습니다."
grep -Fq 'ERROR 2003' "$TEST_TMP/failure-mysql-connect/output.log" \
  || fail_test "MySQL 연결 실패 진단이 사라졌습니다."
grep -Fq '[REDACTED_DEV_DB_HOST]' "$TEST_TMP/failure-mysql-connect/output.log" \
  || fail_test "MySQL 연결 실패 로그에서 DB host가 마스킹되지 않았습니다."
[[ "$(grep -Fc '::error title=dev DB reset 실패::' \
    "$TEST_TMP/failure-mysql-connect/output.log")" == "1" ]] \
  || fail_test "MySQL 연결 실패에서 reset 오류 안내가 중복 출력됐습니다."

run_case failure-app-stop app-stop \
  --confirm-dev-reset main pm-full-requested-catalog false
grep -Eq ' compose .* stop app ' "$TEST_TMP/failure-app-stop/commands.log" \
  || fail_test "app-stop 실패 주입이 앱 중지 단계에 도달하지 않았습니다."
[[ ! -e "$TEST_TMP/failure-app-stop/deploy/.dev-db-reset-incomplete" ]] \
  || fail_test "clean 전 app-stop 실패는 marker를 정리해야 합니다."
grep -Fq '실패/종료 단계: `안전 marker 생성과 앱 중지`' \
  "$TEST_TMP/failure-app-stop/report.md" \
  || fail_test "app-stop 실패 report의 단계 안내가 정확하지 않습니다."

while IFS='|' read -r failure_stage expected_report_stage; do
  run_case "failure-${failure_stage}" "$failure_stage" \
    --confirm-dev-reset main pm-full-requested-catalog false
  [[ -e "$TEST_TMP/failure-${failure_stage}/deploy/.dev-db-reset-incomplete" ]] \
    || fail_test "$failure_stage 실패는 incomplete marker를 유지해야 합니다."
  ! grep -Eq ' compose .* up --detach app ' "$TEST_TMP/failure-${failure_stage}/commands.log" \
    || fail_test "$failure_stage 실패 뒤 앱을 재기동했습니다."
  grep -Fq "실패/종료 단계: \`${expected_report_stage}\`" \
    "$TEST_TMP/failure-${failure_stage}/report.md" \
    || fail_test "$failure_stage 실패 report의 단계 안내가 정확하지 않습니다."
done <<'EOF'
flyway-clean|Flyway clean
flyway-migrate|Flyway migrate
base|Base Seed 적용
scenario|Scenario Seed 적용
verify|Scenario와 UTF-8 검증
utf8|Scenario와 UTF-8 검증
playground|PM dev playground 적용과 검증
playground-verify|PM dev playground 적용과 검증
flyway-validate|최종 Flyway validate와 연결 검증
final-charset|최종 Flyway validate와 연결 검증
EOF

[[ "$(< "$TEST_TMP/failure-flyway-clean/exit-code")" == "43" ]] \
  || fail_test "Flyway 실패 종료 코드가 보존되지 않았습니다."
grep -Fq 'ERROR: Unable to obtain connection' \
  "$TEST_TMP/failure-flyway-clean/output.log" \
  || fail_test "Flyway 연결 실패 진단이 사라졌습니다."
grep -Fq '[REDACTED_DEV_DB_URL]' \
  "$TEST_TMP/failure-flyway-clean/output.log" \
  || fail_test "Flyway 연결 실패 로그에서 JDBC URL이 마스킹되지 않았습니다."

for failure_stage in app-start health persona; do
  run_case "failure-${failure_stage}" "$failure_stage" \
    --confirm-dev-reset main pm-full-requested-catalog false
  [[ ! -e "$TEST_TMP/failure-${failure_stage}/deploy/.dev-db-reset-incomplete" ]] \
    || fail_test "$failure_stage 실패는 DB 검증 뒤이므로 marker가 없어야 합니다."
  grep -Eq 'DB를 다시 (지우지|초기화하지) 말고' \
    "$TEST_TMP/failure-${failure_stage}/output.log" \
    "$TEST_TMP/failure-${failure_stage}/report.md" \
    || fail_test "$failure_stage 실패 안내가 DB 재초기화를 금지하지 않았습니다."
done
grep -Eq ' compose .* up --detach app ' "$TEST_TMP/failure-app-start/commands.log" \
  || fail_test "app-start 실패 주입이 앱 재기동 단계에 도달하지 않았습니다."
grep -Fq '/actuator/health' "$TEST_TMP/failure-health/commands.log" \
  || fail_test "health 실패 주입이 health 단계에 도달하지 않았습니다."
grep -Fq '/dev/auth/personas' "$TEST_TMP/failure-persona/commands.log" \
  || fail_test "persona 실패 주입이 smoke 단계에 도달하지 않았습니다."
grep -Fq '현재 앱 상태: 컨테이너 실행 중(health 미확인)' \
  "$TEST_TMP/failure-app-start/report.md" \
  || fail_test "app-start 실패 report가 실제 컨테이너 상태를 확인하지 않았습니다."
grep -Fq '현재 앱 상태: 컨테이너 실행 중(health 미확인)' \
  "$TEST_TMP/failure-health/report.md" \
  || fail_test "health 실패 report가 실행 중인 컨테이너 상태를 누락했습니다."
grep -Fq '현재 앱 상태: 정상 실행(health UP)' \
  "$TEST_TMP/failure-persona/report.md" \
  || fail_test "persona 실패 report가 이미 확인한 health UP 상태를 잃었습니다."

run_case failure-persona-encoding persona-encoding \
  --confirm-dev-reset main pm-full-requested-catalog false
grep -Fq '정확한 한글 닉네임' \
  "$TEST_TMP/failure-persona-encoding/output.log" \
  "$TEST_TMP/failure-persona-encoding/report.md" \
  || fail_test "persona 문자셋 실패의 한글 복구 안내가 없습니다."
grep -Fq '현재 앱 상태: 정상 실행(health UP)' \
  "$TEST_TMP/failure-persona-encoding/report.md" \
  || fail_test "문자셋 smoke 실패 report가 이미 확인한 health UP 상태를 잃었습니다."

for success_scenario in \
  matching-price-vivaldi \
  matching-no-candidate-alpensia \
  matching-multi-request-oak \
  pm-full-requested-catalog; do
  run_case "success-${success_scenario}" "" \
    --confirm-dev-reset main "$success_scenario" true
done

(
  RUN_CASE_DEPLOYED_IMAGE="$EXPECTED_DIGEST_IMAGE" \
  RUN_CASE_RUNNING_IMAGE="$EXPECTED_DIGEST_IMAGE" \
    run_case success-digest-image "" \
      --confirm-dev-reset main matching-price-vivaldi true
)

for non_pm_scenario in \
  matching-price-vivaldi \
  matching-no-candidate-alpensia \
  matching-multi-request-oak; do
  ! grep -Fq 'SQL_STAGE=playground' \
    "$TEST_TMP/success-${non_pm_scenario}/commands.log" \
    || fail_test "$non_pm_scenario 시나리오에 PM playground overlay를 적용했습니다."
done
grep -Fq 'SQL_STAGE=playground' \
  "$TEST_TMP/success-pm-full-requested-catalog/commands.log" \
  || fail_test "PM 시나리오에 dev playground overlay가 적용되지 않았습니다."
assert_pm_success_order \
  "$TEST_TMP/success-pm-full-requested-catalog/commands.log"
[[ "$(grep -Fc 'character_set_client' \
    "$TEST_TMP/success-pm-full-requested-catalog/commands.log")" == "2" ]] \
  || fail_test "reset 성공 전에 최종 schema UTF-8 계약을 다시 확인하지 않았습니다."

run_migrate_case migration-blocked-by-marker "" true false
! grep -Eq ' flyway/flyway:' "$TEST_TMP/migration-blocked-by-marker/commands.log" \
  || fail_test "incomplete reset marker가 있는 배포에서 Flyway를 실행했습니다."
run_migrate_case migration-account-collision "" false false ssing_migration
! grep -Eq ' flyway/flyway:' "$TEST_TMP/migration-account-collision/commands.log" \
  || fail_test "runtime과 migration 계정이 같은 배포에서 Flyway를 실행했습니다."
run_migrate_case migration-mysql-connect mysql-connect false false
[[ "$(< "$TEST_TMP/migration-mysql-connect/exit-code")" == "50" ]] \
  || fail_test "migration MySQL 연결 실패 종료 코드가 보존되지 않았습니다."
grep -Fq 'ERROR 2003' "$TEST_TMP/migration-mysql-connect/output.log" \
  || fail_test "migration MySQL 연결 실패 진단이 사라졌습니다."
grep -Fq '[REDACTED_DEV_DB_HOST]' \
  "$TEST_TMP/migration-mysql-connect/output.log" \
  || fail_test "migration MySQL 연결 실패 로그에서 DB host가 마스킹되지 않았습니다."
[[ "$(grep -Fc '::error title=dev DB migration 실패::' \
    "$TEST_TMP/migration-mysql-connect/output.log")" == "1" ]] \
  || fail_test "MySQL 연결 실패에서 migration 오류 안내가 중복 출력됐습니다."
run_migrate_case migration-failure flyway-migrate false false
[[ "$(< "$TEST_TMP/migration-failure/exit-code")" == "43" ]] \
  || fail_test "migration Flyway 실패 종료 코드가 보존되지 않았습니다."
! grep -Eq ' compose .* up ' "$TEST_TMP/migration-failure/commands.log" \
  || fail_test "migration 실패 뒤 앱을 재기동했습니다."
run_migrate_case migration-final-charset migration-final-charset false false
grep -Fq '문자셋이 모두 utf8mb4가 아닙니다' \
  "$TEST_TMP/migration-final-charset/output.log" \
  || fail_test "migration 후 최종 UTF-8 계약 실패를 놓쳤습니다."
run_migrate_case migration-success "" false true
[[ "$(grep -Fc 'character_set_client' \
    "$TEST_TMP/migration-success/commands.log")" == "2" ]] \
  || fail_test "일반 migration 성공 전에 최종 schema UTF-8 계약을 다시 확인하지 않았습니다."

run_deploy_prepare_case deploy-prepare-invalid-confirm "" false false \
  wrong-confirm main "$EXPECTED_DIGEST_IMAGE" true false
assert_no_mutation_command "$TEST_TMP/deploy-prepare-invalid-confirm/commands.log"
run_deploy_prepare_case deploy-prepare-invalid-ref "" false false \
  --confirm-dev-deploy-reset feature "$EXPECTED_DIGEST_IMAGE" true false
assert_no_mutation_command "$TEST_TMP/deploy-prepare-invalid-ref/commands.log"
stale_digest_image="teamssing/server:dev-1111111111111111111111111111111111111111@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
run_deploy_prepare_case deploy-prepare-stale-image "" false false \
  --confirm-dev-deploy-reset main "$stale_digest_image" true false
assert_no_mutation_command "$TEST_TMP/deploy-prepare-stale-image/commands.log"

for failure_stage in compose-config compose-pull docker-name-conflict flyway-info mysql-connect; do
  run_deploy_prepare_case "deploy-preflight-${failure_stage}" \
    "$failure_stage" false false --preflight-dev-deploy main
  assert_no_destructive_deploy_command \
    "$TEST_TMP/deploy-preflight-${failure_stage}/commands.log"
done
run_deploy_prepare_case deploy-preflight-success "" false true \
  --preflight-dev-deploy main

run_deploy_prepare_case deploy-prepare-without-preflight "" false false \
  --confirm-dev-deploy-reset main "$EXPECTED_DIGEST_IMAGE" true false
assert_no_destructive_deploy_command \
  "$TEST_TMP/deploy-prepare-without-preflight/commands.log"

run_deploy_prepare_case deploy-prepare-stale-preflight "" false false \
  --confirm-dev-deploy-reset main "$EXPECTED_DIGEST_IMAGE" true true true
assert_no_destructive_deploy_command \
  "$TEST_TMP/deploy-prepare-stale-preflight/commands.log"
[[ ! -e "$TEST_TMP/deploy-prepare-stale-preflight/deploy/.dev-deploy-preflight" ]] \
  || fail_test "변경된 후보 설정의 오래된 사전 검증 증거를 삭제하지 않았습니다."

run_deploy_prepare_case deploy-prepare-app-stop app-stop false false
[[ ! -e "$TEST_TMP/deploy-prepare-app-stop/deploy/.dev-db-reset-incomplete" ]] \
  || fail_test "자동 배포 clean 전 app-stop 실패는 새 marker를 정리해야 합니다."

for failure_stage in flyway-clean flyway-migrate flyway-validate base base-verify utf8; do
  run_deploy_prepare_case "deploy-prepare-${failure_stage}" "$failure_stage" false false
  [[ -e "$TEST_TMP/deploy-prepare-${failure_stage}/deploy/.dev-db-reset-incomplete" ]] \
    || fail_test "자동 배포 ${failure_stage} 실패는 marker를 유지해야 합니다."
  ! grep -Eq ' compose .* up ' \
      "$TEST_TMP/deploy-prepare-${failure_stage}/commands.log" \
    || fail_test "자동 배포 ${failure_stage} 실패 뒤 앱을 재기동했습니다."
done

run_deploy_prepare_case deploy-prepare-success "" false true
run_deploy_prepare_case deploy-prepare-first-deploy "" false true \
  --confirm-dev-deploy-reset main "$EXPECTED_DIGEST_IMAGE" false
grep -Eq 'SSING_RUNTIME_ENV_FILE=\.env\.next .* compose .* stop app ' \
  "$TEST_TMP/deploy-prepare-first-deploy/commands.log" \
  || fail_test "첫 배포에서 .env.next를 Compose runtime env로 사용하지 않았습니다."
run_deploy_prepare_case deploy-prepare-recovers-marker "" true true
grep -Fq '최신 main 기준 전체 초기화로 복구합니다' \
  "$TEST_TMP/deploy-prepare-recovers-marker/output.log" \
  || fail_test "기존 marker를 최신 main 전체 초기화로 복구한다는 안내가 없습니다."

run_deploy_prepare_case deploy-prepare-preexisting-app-stop app-stop true false
[[ -e "$TEST_TMP/deploy-prepare-preexisting-app-stop/deploy/.dev-db-reset-incomplete" ]] \
  || fail_test "기존 marker를 clean 전 실패가 삭제했습니다."

printf 'dev runner contract test passed\n'
