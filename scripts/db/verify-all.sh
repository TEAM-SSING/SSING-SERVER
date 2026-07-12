#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

scenario_key="${1:-}"
[[ -n "$scenario_key" ]] || fail "scenario key is required"

assert_local_target
assert_scenario_key "$scenario_key"
run_mysql_file "$PROJECT_ROOT/db/seed/scenarios/$scenario_key/verify.sql"
