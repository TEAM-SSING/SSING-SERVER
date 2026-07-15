#!/usr/bin/env bash

set -Eeuo pipefail

fail_install() {
  printf 'dev release 설치 실패: %s\n' "$1" >&2
  exit "${2:-1}"
}

[[ "$#" -eq 3 ]] \
  || fail_install "Usage: $0 <tooling-archive> <release-root> <deploy-sha>" 2

archive="$1"
release_root="$2"
deploy_sha="$3"

[[ -s "$archive" ]] || fail_install "배포 tooling archive가 없거나 비어 있습니다."
[[ -n "$release_root" && "$release_root" == /* ]] \
  || fail_install "release root는 비어 있지 않은 절대 경로여야 합니다."
[[ "$deploy_sha" =~ ^[0-9a-f]{40}$ ]] \
  || fail_install "배포 SHA는 40자리 소문자 Git commit SHA여야 합니다."

install -d -m 700 "$release_root"
release_dir="$release_root/$deploy_sha"
release_tmp="$(mktemp -d "$release_root/.${deploy_sha}.tmp.XXXXXX")"

cleanup_tmp() {
  rm -rf -- "$release_tmp"
}
trap cleanup_tmp EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

tar -xzf "$archive" -C "$release_tmp"

for required_file in \
  "$release_tmp/scripts/db/prepare-dev-deploy-db.sh" \
  "$release_tmp/db/seed/verify-base.sql" \
  "$release_tmp/db/seed/verify-utf8.sql"; do
  [[ -s "$required_file" ]] \
    || fail_install "필수 배포 파일 ${required_file#"$release_tmp/"}이 없습니다."
done

shopt -s nullglob
migration_files=("$release_tmp/src/main/resources/db/migration"/*.sql)
base_seed_files=("$release_tmp/db/seed/base"/*.sql)
(( ${#migration_files[@]} > 0 )) \
  || fail_install "Flyway migration SQL이 없습니다."
(( ${#base_seed_files[@]} > 0 )) \
  || fail_install "base seed SQL이 없습니다."

chmod +x "$release_tmp/scripts/db"/*.sh

# 같은 SHA를 재시도할 때도 검증을 끝낸 새 snapshot으로 통째로 교체해 stale SQL을 남기지 않는다.
rm -rf -- "$release_dir"
mv -- "$release_tmp" "$release_dir"
trap - EXIT HUP INT TERM

printf 'dev release 설치 완료: releases/%s\n' "$deploy_sha"
