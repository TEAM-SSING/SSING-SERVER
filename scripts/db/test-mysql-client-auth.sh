#!/usr/bin/env bash

set -euo pipefail

readonly TEST_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$TEST_SCRIPT_DIR/common.sh"

fail_test() {
  printf 'mysql client auth test failed: %s\n' "$*" >&2
  exit 1
}

assert_private_file_mode() {
  local file_path="$1"
  local file_mode

  if file_mode="$(stat -c '%a' "$file_path" 2>/dev/null)"; then
    :
  else
    file_mode="$(stat -f '%Lp' "$file_path")"
  fi

  [[ "$file_mode" == "600" ]] || fail_test "credential file mode must be 600: $file_mode"
}

docker() {
  local arguments=("$@")
  local mount_spec=""
  local mysql_index=-1
  local index

  [[ -z "${MYSQL_PWD+x}" ]] || fail_test "MYSQL_PWD must not be passed to Docker"
  [[ -z "${SSING_LOCAL_DB_ROOT_PASSWORD+x}" ]] \
    || fail_test "SSING_LOCAL_DB_ROOT_PASSWORD must not be inherited by Docker"

  for ((index = 0; index < ${#arguments[@]}; index++)); do
    if [[ "${arguments[$index]}" == "--env" && "${arguments[$((index + 1))]:-}" == MYSQL_PWD* ]]; then
      fail_test "Docker arguments must not include MYSQL_PWD"
    fi
    if [[ "${arguments[$index]}" == "--mount" ]]; then
      mount_spec="${arguments[$((index + 1))]:-}"
    fi
    if [[ "${arguments[$index]}" == "mysql" ]]; then
      mysql_index="$index"
    fi
  done

  [[ -n "$mount_spec" ]] || fail_test "credential file must be mounted into Docker"
  [[ ",$mount_spec," == *,readonly,* ]] || fail_test "credential file mount must be read-only"
  [[ "$mysql_index" -ge 0 ]] || fail_test "mysql client command was not found"
  [[ "${arguments[$((mysql_index + 1))]:-}" == "--defaults-extra-file=/run/secrets/ssing-mysql-client.cnf" ]] \
    || fail_test "--defaults-extra-file must be the first mysql option"
  [[ " ${arguments[*]} " != *" ${EXPECTED_PASSWORD} "* ]] \
    || fail_test "password must not appear in Docker arguments"

  local source_path=""
  local destination_path=""
  local mount_option
  local mount_options
  IFS=',' read -r -a mount_options <<< "$mount_spec"
  for mount_option in "${mount_options[@]}"; do
    case "$mount_option" in
      src=*) source_path="${mount_option#src=}" ;;
      dst=*) destination_path="${mount_option#dst=}" ;;
    esac
  done

  [[ "$destination_path" == "/run/secrets/ssing-mysql-client.cnf" ]] \
    || fail_test "unexpected credential mount destination: $destination_path"
  [[ -f "$source_path" ]] || fail_test "credential file was not created"
  [[ ! -L "$source_path" ]] || fail_test "credential file must not be a symbolic link"
  assert_private_file_mode "$source_path"
  grep -Fxq '[client]' "$source_path" || fail_test "client section is missing"
  grep -Fxq 'password="local secret#42;\"quoted\"\\path"' "$source_path" \
    || fail_test "escaped password entry is missing"

  printf '%s\n' "$source_path" > "$CAPTURE_FILE"
  if [[ -n "${FAKE_DOCKER_SIGNAL:-}" ]]; then
    command bash -c 'kill -s "$1" "$PPID"' _ "$FAKE_DOCKER_SIGNAL"
  fi
  return "${FAKE_DOCKER_EXIT_CODE:-0}"
}

assert_credential_file_removed() {
  local credential_file
  credential_file="$(<"$CAPTURE_FILE")"
  [[ -n "$credential_file" ]] || fail_test "credential file path was not captured"
  [[ ! -e "$credential_file" ]] || fail_test "credential file was not removed: $credential_file"
}

CAPTURE_FILE="$(mktemp /tmp/ssing-mysql-client-test.XXXXXX)"
trap 'rm -f "$CAPTURE_FILE"' EXIT

SSING_LOCAL_DB_CONTAINER="ssing-local-mysql"
SSING_LOCAL_DB_NAME="ssing_local"
EXPECTED_PASSWORD='local secret#42;"quoted"\path'
SSING_LOCAL_DB_ROOT_PASSWORD="$EXPECTED_PASSWORD"
MYSQL_PWD="legacy-password-must-not-leak"
export SSING_LOCAL_DB_ROOT_PASSWORD MYSQL_PWD

FAKE_DOCKER_EXIT_CODE=0
run_mysql_query "SELECT 1" >/dev/null
assert_credential_file_removed

: > "$CAPTURE_FILE"
sql_file="$(mktemp /tmp/ssing-mysql-client-sql.XXXXXX)"
trap 'rm -f "$CAPTURE_FILE" "$sql_file"' EXIT
printf 'SELECT 1;\n' > "$sql_file"
run_mysql_file "$sql_file" >/dev/null
assert_credential_file_removed

: > "$CAPTURE_FILE"
FAKE_DOCKER_EXIT_CODE=24
if run_mysql_file "$sql_file" >/dev/null; then
  fail_test "mysql file failure must be propagated"
else
  exit_code=$?
fi
[[ "$exit_code" -eq 24 ]] || fail_test "unexpected mysql file exit code: $exit_code"
assert_credential_file_removed

: > "$CAPTURE_FILE"
FAKE_DOCKER_EXIT_CODE=23
if run_mysql_query "SELECT 1" >/dev/null; then
  fail_test "mysql client failure must be propagated"
else
  exit_code=$?
fi
[[ "$exit_code" -eq 23 ]] || fail_test "unexpected mysql client exit code: $exit_code"
assert_credential_file_removed

: > "$CAPTURE_FILE"
FAKE_DOCKER_EXIT_CODE=0
FAKE_DOCKER_SIGNAL=TERM
if run_mysql_query "SELECT 1" >/dev/null; then
  fail_test "mysql client signal must be propagated"
else
  exit_code=$?
fi
[[ "$exit_code" -eq 143 ]] || fail_test "unexpected mysql client signal exit code: $exit_code"
assert_credential_file_removed
unset FAKE_DOCKER_SIGNAL

: > "$CAPTURE_FILE"
FAKE_DOCKER_EXIT_CODE=0
SSING_LOCAL_DB_ROOT_PASSWORD=$'invalid\npassword'
if run_mysql_query "SELECT 1" >/dev/null 2>&1; then
  fail_test "passwords containing LF must be rejected"
fi
[[ ! -s "$CAPTURE_FILE" ]] || fail_test "Docker must not run for an invalid password"

printf 'mysql client auth test passed\n'
