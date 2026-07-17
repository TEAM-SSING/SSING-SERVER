#!/usr/bin/env bash

set -euo pipefail

readonly TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$TEST_DIR/../.." && pwd)"
readonly ADMINER_IMAGE="adminer:5.4.2-standalone"

docker run --rm \
  --entrypoint php \
  --mount "type=bind,src=${PROJECT_ROOT},dst=/work,readonly" \
  "$ADMINER_IMAGE" \
  -l /work/deploy/adminer/001-ssing-autologin.php

docker run --rm \
  --entrypoint php \
  --mount "type=bind,src=${PROJECT_ROOT},dst=/work,readonly" \
  "$ADMINER_IMAGE" \
  /work/scripts/db/test-dev-adminer-autologin.php
