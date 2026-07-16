#!/usr/bin/env bash

set -euo pipefail

readonly TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly INSTALLER="$TEST_DIR/install-dev-release.sh"
readonly TEST_SHA="0123456789abcdef0123456789abcdef01234567"
readonly OTHER_SHA="1111111111111111111111111111111111111111"
readonly INVALID_SHA="2222222222222222222222222222222222222222"
readonly MISSING_DEV_COMMON_SHA="3333333333333333333333333333333333333333"
readonly MISSING_COMMON_SHA="4444444444444444444444444444444444444444"

fail_test() {
  printf 'dev release 설치기 계약 테스트 실패: %s\n' "$*" >&2
  exit 1
}

create_valid_snapshot() {
  local snapshot_root="$1"
  local label="$2"

  mkdir -p \
    "$snapshot_root/scripts/db" \
    "$snapshot_root/src/main/resources/db/migration" \
    "$snapshot_root/db/seed/base"
  printf '#!/usr/bin/env bash\nexit 0\n' \
    > "$snapshot_root/scripts/db/prepare-dev-deploy-db.sh"
  printf '#!/usr/bin/env bash\n' \
    > "$snapshot_root/scripts/db/dev-common.sh"
  printf '#!/usr/bin/env bash\n' \
    > "$snapshot_root/scripts/db/common.sh"
  printf '%s\n' '-- migration fixture' "SELECT '${label}-migration';" \
    > "$snapshot_root/src/main/resources/db/migration/V1__fixture.sql"
  printf '%s\n' '-- base fixture' "SELECT '${label}-base';" \
    > "$snapshot_root/db/seed/base/001_fixture.sql"
  printf '%s\n' '-- base verify fixture' "SELECT '${label}-verify';" \
    > "$snapshot_root/db/seed/verify-base.sql"
  printf '%s\n' '-- utf8 verify fixture' "SELECT '${label}-utf8';" \
    > "$snapshot_root/db/seed/verify-utf8.sql"
}

assert_missing_helper_rejected() {
  local helper_path="$1"
  local deploy_sha="$2"
  local label="$3"
  local snapshot_root="$TEST_TMP/${label}-snapshot"
  local archive="$TEST_TMP/${label}-tooling.tgz"
  local output_log="$TEST_TMP/${label}-output.log"
  local release_dir="$release_root/$deploy_sha"
  local exit_code

  create_valid_snapshot "$snapshot_root" "$label"
  rm -f -- "$snapshot_root/$helper_path"
  tar -czf "$archive" -C "$snapshot_root" .

  mkdir -p "$release_dir"
  printf 'existing\n' > "$release_dir/sentinel"

  set +e
  bash "$INSTALLER" "$archive" "$release_root" "$deploy_sha" \
    > "$output_log" 2>&1
  exit_code=$?
  set -e

  [[ "$exit_code" -ne 0 ]] \
    || fail_test "${helper_path}가 없는 archive를 정상 release로 설치했습니다."
  [[ -s "$release_dir/sentinel" ]] \
    || fail_test "${helper_path} 누락 실패가 기존 정상 release를 삭제했습니다."
  grep -Fq "dev release 설치 실패" "$output_log" \
    || fail_test "${helper_path} 누락 실패를 한글 오류로 안내하지 않았습니다."
  grep -Fq "$helper_path" "$output_log" \
    || fail_test "누락된 helper 경로 ${helper_path}를 오류에 표시하지 않았습니다."
  if compgen -G "$release_root/.${deploy_sha}.tmp.*" >/dev/null; then
    fail_test "${helper_path} 누락 실패 뒤 임시 release 디렉터리가 남았습니다."
  fi
}

TEST_TMP="$(mktemp -d /tmp/ssing-dev-release-test.XXXXXX)"
trap 'rm -rf -- "$TEST_TMP"' EXIT

release_root="$TEST_TMP/releases"
snapshot_root="$TEST_TMP/snapshot"
archive="$TEST_TMP/tooling.tgz"
mkdir -p \
  "$release_root/$TEST_SHA/db/seed/base" \
  "$release_root/$OTHER_SHA"
printf 'stale\n' > "$release_root/$TEST_SHA/db/seed/base/stale.sql"
printf 'keep\n' > "$release_root/$OTHER_SHA/sentinel"

create_valid_snapshot "$snapshot_root" fresh
tar -czf "$archive" -C "$snapshot_root" .

valid_output="$(bash "$INSTALLER" "$archive" "$release_root" "$TEST_SHA")"

[[ -s "$release_root/$TEST_SHA/db/seed/base/001_fixture.sql" ]] \
  || fail_test "새 SHA snapshot의 base seed가 설치되지 않았습니다."
[[ ! -e "$release_root/$TEST_SHA/db/seed/base/stale.sql" ]] \
  || fail_test "새 SHA snapshot에 이전 배포의 stale SQL이 남았습니다."
[[ -s "$release_root/$OTHER_SHA/sentinel" ]] \
  || fail_test "다른 SHA의 정상 release를 삭제했습니다."
[[ "$valid_output" == *"dev release archive 검증"* ]] \
  || fail_test "정상 설치의 archive 검증 진행 상황을 한글로 안내하지 않았습니다."
[[ "$valid_output" == *"dev release 설치 완료"* ]] \
  || fail_test "정상 설치 완료 상황을 한글로 안내하지 않았습니다."

assert_missing_helper_rejected \
  "scripts/db/dev-common.sh" \
  "$MISSING_DEV_COMMON_SHA" \
  "missing-dev-common"
assert_missing_helper_rejected \
  "scripts/db/common.sh" \
  "$MISSING_COMMON_SHA" \
  "missing-common"

invalid_snapshot="$TEST_TMP/invalid-snapshot"
invalid_archive="$TEST_TMP/invalid-tooling.tgz"
mkdir -p \
  "$invalid_snapshot/scripts/db" \
  "$release_root/$INVALID_SHA"
printf '#!/usr/bin/env bash\nexit 0\n' \
  > "$invalid_snapshot/scripts/db/prepare-dev-deploy-db.sh"
printf 'existing\n' > "$release_root/$INVALID_SHA/sentinel"
tar -czf "$invalid_archive" -C "$invalid_snapshot" .

set +e
bash "$INSTALLER" "$invalid_archive" "$release_root" "$INVALID_SHA" \
  > "$TEST_TMP/invalid-output.log" 2>&1
invalid_exit_code=$?
set -e

[[ "$invalid_exit_code" -ne 0 ]] \
  || fail_test "필수 SQL이 없는 archive를 정상 release로 설치했습니다."
[[ -s "$release_root/$INVALID_SHA/sentinel" ]] \
  || fail_test "불완전 archive 실패가 기존 정상 release를 삭제했습니다."
if compgen -G "$release_root/.${INVALID_SHA}.tmp.*" >/dev/null; then
  fail_test "불완전 archive 실패 뒤 임시 release 디렉터리가 남았습니다."
fi

printf 'dev release 설치기 계약 테스트 통과\n'
