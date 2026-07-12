#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

confirmation="${1:-}"
scenario_key="${2:-matching-price-vivaldi}"

[[ "$confirmation" == "--confirm-local-reset" ]] || {
  printf 'Usage: %s --confirm-local-reset [scenario-key]\n' "$0" >&2
  exit 2
}

export SSING_SEED_TARGET_ENV="local"
export SSING_LOCAL_DB_CONTAINER="${SSING_LOCAL_DB_CONTAINER:-ssing-local-mysql}"
export SSING_LOCAL_DB_NAME="${SSING_LOCAL_DB_NAME:-ssing_local}"
export SSING_LOCAL_DB_ROOT_PASSWORD="${SSING_LOCAL_DB_ROOT_PASSWORD:-ssing_root}"

assert_local_target
assert_scenario_key "$scenario_key"
require_command docker

docker compose \
  --file "$PROJECT_ROOT/docker-compose.local.yml" \
  up --detach --wait mysql

exec "$SCRIPT_DIR/reset-core.sh" local "$scenario_key"
