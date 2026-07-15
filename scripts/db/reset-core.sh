#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

confirmation="${1:-}"
target_env="${2:-}"
scenario_key="${3:-}"

# Flyway clean 진입 전 wrapper와 core 양쪽에서 명시적 local reset 확인을 요구한다.
[[ "$confirmation" == "--confirm-local-reset" ]] || {
  printf 'Usage: %s --confirm-local-reset local [scenario-key]\n' "$0" >&2
  exit 2
}
[[ "$target_env" == "local" ]] || fail "reset-core currently accepts only: local"
[[ -n "$scenario_key" ]] || fail "scenario key is required"

require_command docker
assert_local_target
assert_scenario_key "$scenario_key"

printf 'Reset target: environment=%s container=%s database=%s scenario=%s\n' \
  "$target_env" \
  "$SSING_LOCAL_DB_CONTAINER" \
  "$SSING_LOCAL_DB_NAME" \
  "$scenario_key"

run_flyway -cleanDisabled=false clean
run_flyway migrate
run_flyway validate

apply_sql_directory "$PROJECT_ROOT/db/seed/base"
run_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$scenario_key/seed.sql"
"$SCRIPT_DIR/verify-all.sh" "$scenario_key"
run_mysql_file "$PROJECT_ROOT/db/seed/verify-utf8.sql"

# A second migrate must report no pending versioned migrations.
run_flyway migrate
run_flyway validate

run_mysql_query "
SELECT persona_key, member_id
FROM dev_personas
ORDER BY persona_key;
"
