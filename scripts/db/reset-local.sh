#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

confirmation="${1:-}"
seed_target="${2:-$IDLE_SEED_TARGET}"

[[ "$confirmation" == "--confirm-local-reset" ]] || {
  printf 'Usage: %s --confirm-local-reset [seed-target]\n' "$0" >&2
  exit 2
}

export SSING_SEED_TARGET_ENV="local"
export SSING_LOCAL_DB_CONTAINER="${SSING_LOCAL_DB_CONTAINER:-ssing-local-mysql}"
export SSING_LOCAL_DB_NAME="${SSING_LOCAL_DB_NAME:-ssing_local}"
export SSING_LOCAL_DB_ROOT_PASSWORD="${SSING_LOCAL_DB_ROOT_PASSWORD:-ssing_root}"
export SSING_LOCAL_COMPOSE_PROJECT="${SSING_LOCAL_COMPOSE_PROJECT:-ssing}"

assert_local_target
assert_seed_target "$seed_target"
require_command docker

docker compose \
  --project-name "$SSING_LOCAL_COMPOSE_PROJECT" \
  --file "$PROJECT_ROOT/docker-compose.local.yml" \
  up --detach --wait mysql

exec "$SCRIPT_DIR/reset-core.sh" \
  "$confirmation" \
  local \
  "$seed_target"
