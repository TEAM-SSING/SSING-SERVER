#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

seed_target="${1:-$IDLE_SEED_TARGET}"

assert_local_target
assert_seed_target "$seed_target"
if is_idle_seed_target "$seed_target"; then
  run_mysql_file "$PROJECT_ROOT/db/seed/verify-base.sql"
else
  run_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$seed_target/verify.sql"
fi
