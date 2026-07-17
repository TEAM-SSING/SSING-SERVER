#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

confirmation="${1:-}"
target_env="${2:-}"
seed_target="${3:-$IDLE_SEED_TARGET}"

# Flyway clean 진입 전 wrapper와 core 양쪽에서 명시적 local reset 확인을 요구한다.
[[ "$confirmation" == "--confirm-local-reset" ]] || {
  printf 'Usage: %s --confirm-local-reset local [seed-target]\n' "$0" >&2
  exit 2
}
[[ "$target_env" == "local" ]] || fail "reset-core currently accepts only: local"
require_command docker
assert_local_target
assert_seed_target "$seed_target"

printf 'Reset target: environment=%s container=%s database=%s seed_target=%s\n' \
  "$target_env" \
  "$SSING_LOCAL_DB_CONTAINER" \
  "$SSING_LOCAL_DB_NAME" \
  "$seed_target"

run_flyway -cleanDisabled=false clean
run_flyway migrate
run_flyway validate

apply_sql_directory "$PROJECT_ROOT/db/seed/base"
run_mysql_file "$PROJECT_ROOT/db/seed/verify-base.sql"
if ! is_idle_seed_target "$seed_target"; then
  run_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$seed_target/seed.sql"
  run_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$seed_target/verify.sql"
fi
run_mysql_file "$PROJECT_ROOT/db/seed/verify-utf8.sql"

# A second migrate must report no pending versioned migrations.
run_flyway migrate
run_flyway validate

run_mysql_query "
SELECT persona_key, member_id
FROM dev_personas
ORDER BY persona_key;
"
