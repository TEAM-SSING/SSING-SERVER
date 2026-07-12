#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

confirmation="${1:-}"
[[ "$confirmation" == "--confirm-local-reset" ]] || {
  printf 'Usage: %s --confirm-local-reset\n' "$0" >&2
  exit 2
}

scenario_found=false
for scenario_directory in "$PROJECT_ROOT"/db/seed/scenarios/*; do
  [[ -d "$scenario_directory" ]] || continue
  scenario_found=true
  scenario_key="$(basename "$scenario_directory")"
  printf 'Resetting seed scenario: %s\n' "$scenario_key"
  "$SCRIPT_DIR/reset-local.sh" "$confirmation" "$scenario_key"
done

[[ "$scenario_found" == true ]] || fail "no seed scenarios found"
