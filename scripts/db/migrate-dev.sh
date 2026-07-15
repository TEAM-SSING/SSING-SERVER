#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/dev-common.sh"

trap 'printf "::error title=dev DB migration 실패::기존 앱은 재기동하지 않았습니다. 대상 설정, 전용 migration 계정, Flyway 로그를 확인하세요.\n" >&2' ERR

printf 'dev DB 비파괴 migration 사전 검사를 시작합니다.\n'
dev_require_command sudo
dev_require_command docker
assert_dev_target
assert_no_incomplete_dev_reset
assert_dev_account_separation
select_dev_migration_account
assert_dev_connection_contract

printf 'Flyway migrate와 validate를 실행합니다.\n'
run_dev_flyway migrate
run_dev_flyway validate
assert_dev_connection_contract

printf 'dev DB migration과 validate가 완료되었습니다.\n'
