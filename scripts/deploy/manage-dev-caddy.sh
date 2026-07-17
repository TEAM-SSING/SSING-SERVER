#!/usr/bin/env bash

set -euo pipefail

COMMAND="${1:-}"
MARKER_NAME=".dev-deploy-caddy-activated"

fail() {
  echo "$1" >&2
  exit 1
}

require_env() {
  local name
  for name in "$@"; do
    if [ -z "${!name:-}" ]; then
      fail "필수 환경변수가 비어 있습니다: $name"
    fi
  done
}

is_valid_domain() {
  local domain="$1"
  if [ "$domain" = ":80" ]; then
    return 0
  fi
  [[ "$domain" != *"://"* \
    && "$domain" != *"/"* \
    && "$domain" != *":"* \
    && "$domain" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]]
}

is_valid_activation_id() {
  [[ "$1" =~ ^[0-9a-f]{40}-[0-9]+-[0-9]+$ ]]
}

compose_with_env() {
  local env_file="$1"
  shift
  sudo env SSING_RUNTIME_ENV_FILE="$env_file" \
    docker compose --env-file "$env_file" -f "$COMPOSE_FILE" "$@"
}

candidate_path_for() {
  printf 'Caddyfile.candidate-%s\n' "$1"
}

rollback_path_for() {
  printf 'Caddyfile.rollback-%s\n' "$1"
}

rollback_domain_path_for() {
  printf 'Caddyfile.rollback-domain-%s\n' "$1"
}

preflight_state_path_for() {
  printf 'Caddyfile.preflight-state-%s\n' "$1"
}

is_valid_container_id() {
  [[ "$1" =~ ^[0-9a-f]{12,64}$ ]]
}

is_valid_sha256() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]]
}

file_sha256() {
  local path="$1"
  local digest_line
  local digest

  [ -f "$path" ] || fail "SHA-256을 계산할 파일이 없습니다: $path"
  digest_line="$(sha256sum "$path")"
  digest="${digest_line%% *}"
  is_valid_sha256 "$digest" || fail "파일 SHA-256을 안전하게 계산하지 못했습니다: $path"
  printf '%s\n' "$digest"
}

read_marker_id() {
  local marker_id
  marker_id="$(< "$MARKER_NAME")"
  if ! is_valid_activation_id "$marker_id"; then
    fail "Caddy 활성화 marker 형식이 올바르지 않습니다: $marker_id"
  fi
  printf '%s\n' "$marker_id"
}

running_caddy_id() {
  local env_file="${1:-.env}"
  compose_with_env "$env_file" ps --status running -q caddy
}

existing_caddy_id() {
  local env_file="${1:-.env}"
  compose_with_env "$env_file" ps --all -q caddy
}

assert_running_caddy() {
  local container_id
  container_id="$(running_caddy_id)"
  if [ -z "$container_id" ]; then
    fail "실행 중인 Caddy 컨테이너가 없습니다."
  fi
}

read_running_domain() {
  local env_file="${1:-.env}"
  local domain
  domain="$(compose_with_env "$env_file" exec -T caddy printenv SSING_DEV_DOMAIN)"
  printf '%s\n' "${domain%$'\r'}"
}

read_container_config_hash() {
  local container_id="$1"
  local config_hash

  is_valid_container_id "$container_id" \
    || fail "Caddy 컨테이너 ID 형식이 올바르지 않습니다."
  config_hash="$(sudo docker inspect \
    --format '{{ index .Config.Labels "com.docker.compose.config-hash" }}' \
    "$container_id")"
  is_valid_sha256 "$config_hash" \
    || fail "실행 중 Caddy의 Compose config hash를 안전하게 읽지 못했습니다."
  printf '%s\n' "$config_hash"
}

read_desired_config_hash() {
  local env_file="$1"
  local desired_hash_line
  local desired_hash

  desired_hash_line="$(compose_with_env "$env_file" config --hash caddy)"
  desired_hash="${desired_hash_line##* }"
  is_valid_sha256 "$desired_hash" \
    || fail "배포할 Caddy Compose config hash를 안전하게 읽지 못했습니다."
  printf '%s\n' "$desired_hash"
}

write_preflight_state() {
  local candidate_caddy="$1"
  local state_path
  local state_tmp
  local mode
  local running_id
  local existing_id
  local container_id="-"
  local running_domain="-"
  local container_hash="-"
  local desired_hash
  local candidate_hash
  local live_hash

  state_path="$(preflight_state_path_for "$CURRENT_ACTIVATION_ID")"
  state_tmp="${state_path}.tmp"
  running_id="$(running_caddy_id .env.next)"
  existing_id="$(existing_caddy_id .env.next)"
  desired_hash="$(read_desired_config_hash .env.next)"
  candidate_hash="$(file_sha256 "$candidate_caddy")"
  live_hash="$(file_sha256 Caddyfile)"

  if [ -n "$running_id" ]; then
    is_valid_container_id "$running_id" \
      || fail "실행 중 Caddy 컨테이너 ID 형식이 올바르지 않습니다."
    [ "$existing_id" = "$running_id" ] \
      || fail "Caddy 컨테이너를 하나로 식별하지 못했습니다."
    mode="running"
    container_id="$running_id"
    running_domain="$(read_running_domain .env.next)"
    is_valid_domain "$running_domain" \
      || fail "실행 중 Caddy의 domain 형식이 올바르지 않습니다."
    [ "$running_domain" = "$SSING_DEV_DOMAIN" ] \
      || fail "Caddy domain 변경은 무중단 Caddyfile reload와 분리해야 합니다."
    container_hash="$(read_container_config_hash "$container_id")"
    [ "$container_hash" = "$desired_hash" ] \
      || fail "Caddy service 설정 변경은 app 배포와 분리된 명시적 전환이 필요합니다."
  elif [ -n "$existing_id" ]; then
    is_valid_container_id "$existing_id" \
      || fail "중지된 Caddy 컨테이너 ID 형식이 올바르지 않습니다."
    mode="stopped"
    container_id="$existing_id"
    container_hash="$(read_container_config_hash "$container_id")"
    echo "중지된 Caddy를 확인했습니다. 활성화 직전 동일 상태인지 다시 검증합니다."
  else
    mode="first_start"
    echo "Caddy 컨테이너가 없어 최초 기동 상태로 기록합니다."
  fi

  {
    printf 'mode=%s\n' "$mode"
    printf 'target_domain=%s\n' "$SSING_DEV_DOMAIN"
    printf 'container_id=%s\n' "$container_id"
    printf 'running_domain=%s\n' "$running_domain"
    printf 'container_hash=%s\n' "$container_hash"
    printf 'desired_hash=%s\n' "$desired_hash"
    printf 'candidate_sha256=%s\n' "$candidate_hash"
    printf 'live_sha256=%s\n' "$live_hash"
  } > "$state_tmp"
  chmod 600 "$state_tmp"
  mv -f "$state_tmp" "$state_path"
}

load_preflight_state() {
  local state_path
  local -a state_lines
  local state_line
  local state_line_count=0

  state_path="$(preflight_state_path_for "$CURRENT_ACTIVATION_ID")"
  [ -f "$state_path" ] \
    || fail "현재 run의 Caddy 사전검사 상태가 없습니다: $state_path"
  while IFS= read -r state_line || [ -n "$state_line" ]; do
    state_lines[$state_line_count]="$state_line"
    state_line_count=$((state_line_count + 1))
  done < "$state_path"
  [ "$state_line_count" -eq 8 ] \
    || fail "Caddy 사전검사 상태 줄 수가 올바르지 않습니다."

  [[ "${state_lines[0]}" == mode=* ]] \
    || fail "Caddy 사전검사 mode가 없습니다."
  [[ "${state_lines[1]}" == target_domain=* ]] \
    || fail "Caddy 사전검사 target domain이 없습니다."
  [[ "${state_lines[2]}" == container_id=* ]] \
    || fail "Caddy 사전검사 container ID가 없습니다."
  [[ "${state_lines[3]}" == running_domain=* ]] \
    || fail "Caddy 사전검사 running domain이 없습니다."
  [[ "${state_lines[4]}" == container_hash=* ]] \
    || fail "Caddy 사전검사 container hash가 없습니다."
  [[ "${state_lines[5]}" == desired_hash=* ]] \
    || fail "Caddy 사전검사 desired hash가 없습니다."
  [[ "${state_lines[6]}" == candidate_sha256=* ]] \
    || fail "Caddy 사전검사 candidate SHA-256이 없습니다."
  [[ "${state_lines[7]}" == live_sha256=* ]] \
    || fail "Caddy 사전검사 live SHA-256이 없습니다."

  PREFLIGHT_MODE="${state_lines[0]#mode=}"
  PREFLIGHT_TARGET_DOMAIN="${state_lines[1]#target_domain=}"
  PREFLIGHT_CONTAINER_ID="${state_lines[2]#container_id=}"
  PREFLIGHT_RUNNING_DOMAIN="${state_lines[3]#running_domain=}"
  PREFLIGHT_CONTAINER_HASH="${state_lines[4]#container_hash=}"
  PREFLIGHT_DESIRED_HASH="${state_lines[5]#desired_hash=}"
  PREFLIGHT_CANDIDATE_SHA="${state_lines[6]#candidate_sha256=}"
  PREFLIGHT_LIVE_SHA="${state_lines[7]#live_sha256=}"

  is_valid_domain "$PREFLIGHT_TARGET_DOMAIN" \
    || fail "Caddy 사전검사 target domain 형식이 올바르지 않습니다."
  is_valid_sha256 "$PREFLIGHT_DESIRED_HASH" \
    || fail "Caddy 사전검사 desired hash 형식이 올바르지 않습니다."
  is_valid_sha256 "$PREFLIGHT_CANDIDATE_SHA" \
    || fail "Caddy 사전검사 candidate SHA-256 형식이 올바르지 않습니다."
  is_valid_sha256 "$PREFLIGHT_LIVE_SHA" \
    || fail "Caddy 사전검사 live SHA-256 형식이 올바르지 않습니다."

  case "$PREFLIGHT_MODE" in
    running)
      is_valid_container_id "$PREFLIGHT_CONTAINER_ID" \
        || fail "Caddy 사전검사 container ID 형식이 올바르지 않습니다."
      is_valid_domain "$PREFLIGHT_RUNNING_DOMAIN" \
        || fail "Caddy 사전검사 running domain 형식이 올바르지 않습니다."
      is_valid_sha256 "$PREFLIGHT_CONTAINER_HASH" \
        || fail "Caddy 사전검사 container hash 형식이 올바르지 않습니다."
      ;;
    stopped)
      is_valid_container_id "$PREFLIGHT_CONTAINER_ID" \
        || fail "Caddy 사전검사 stopped container ID 형식이 올바르지 않습니다."
      [ "$PREFLIGHT_RUNNING_DOMAIN" = "-" ] \
        || fail "중지 상태의 Caddy에 running domain이 기록되었습니다."
      is_valid_sha256 "$PREFLIGHT_CONTAINER_HASH" \
        || fail "Caddy 사전검사 stopped container hash 형식이 올바르지 않습니다."
      ;;
    first_start)
      [ "$PREFLIGHT_CONTAINER_ID" = "-" \
        ] && [ "$PREFLIGHT_RUNNING_DOMAIN" = "-" \
        ] && [ "$PREFLIGHT_CONTAINER_HASH" = "-" ] \
        || fail "최초 기동 Caddy 사전검사 상태가 올바르지 않습니다."
      ;;
    *)
      fail "Caddy 사전검사 실행 상태가 올바르지 않습니다."
      ;;
  esac
}

assert_preflight_state_unchanged() {
  local candidate_caddy="$1"
  local current_running_id
  local current_existing_id
  local current_domain
  local current_container_hash
  local current_desired_hash

  load_preflight_state
  [ "$PREFLIGHT_TARGET_DOMAIN" = "$SSING_DEV_DOMAIN" ] \
    || fail "사전검사 후 Caddy target domain이 변경되었습니다."
  [ "$(file_sha256 "$candidate_caddy")" = "$PREFLIGHT_CANDIDATE_SHA" ] \
    || fail "사전검사 후 Caddy 후보 파일이 변경되었습니다."
  [ "$(file_sha256 Caddyfile)" = "$PREFLIGHT_LIVE_SHA" ] \
    || fail "사전검사 후 현재 Caddyfile이 변경되었습니다."

  current_desired_hash="$(read_desired_config_hash .env)"
  [ "$current_desired_hash" = "$PREFLIGHT_DESIRED_HASH" ] \
    || fail "사전검사 후 Caddy Compose service 설정이 변경되었습니다."
  current_running_id="$(running_caddy_id .env)"
  current_existing_id="$(existing_caddy_id .env)"

  case "$PREFLIGHT_MODE" in
    running)
      [ "$current_running_id" = "$PREFLIGHT_CONTAINER_ID" \
        ] && [ "$current_existing_id" = "$PREFLIGHT_CONTAINER_ID" ] \
        || fail "사전검사 후 실행 중 Caddy 컨테이너가 변경되었습니다."
      current_domain="$(read_running_domain .env)"
      [ "$current_domain" = "$PREFLIGHT_RUNNING_DOMAIN" ] \
        || fail "사전검사 후 실행 중 Caddy domain이 변경되었습니다."
      current_container_hash="$(read_container_config_hash "$current_running_id")"
      [ "$current_container_hash" = "$PREFLIGHT_CONTAINER_HASH" \
        ] && [ "$current_container_hash" = "$current_desired_hash" ] \
        || fail "사전검사 후 실행 중 Caddy config hash가 변경되었습니다."
      ;;
    stopped)
      [ -z "$current_running_id" ] \
        || fail "사전검사 후 중지 상태였던 Caddy가 실행되었습니다."
      [ "$current_existing_id" = "$PREFLIGHT_CONTAINER_ID" ] \
        || fail "사전검사 후 중지된 Caddy 컨테이너가 변경되었습니다."
      current_container_hash="$(read_container_config_hash "$current_existing_id")"
      [ "$current_container_hash" = "$PREFLIGHT_CONTAINER_HASH" ] \
        || fail "사전검사 후 중지된 Caddy config hash가 변경되었습니다."
      ;;
    first_start)
      [ -z "$current_running_id" ] && [ -z "$current_existing_id" ] \
        || fail "사전검사 후 없었던 Caddy 컨테이너가 생성되었습니다."
      ;;
  esac
}

assert_running_caddy_matches_preflight() {
  local candidate_caddy="$1"
  local container_id
  local existing_id
  local running_domain
  local container_hash
  local desired_hash

  container_id="$(running_caddy_id .env)"
  is_valid_container_id "$container_id" \
    || fail "활성화할 Caddy 컨테이너를 하나로 식별하지 못했습니다."
  if [ -n "${ACTIVATION_CADDY_ID:-}" ]; then
    [ "$container_id" = "$ACTIVATION_CADDY_ID" ] \
      || fail "검증을 시작한 Caddy 컨테이너가 활성화 직전에 교체되었습니다."
  fi
  existing_id="$(existing_caddy_id .env)"
  [ "$existing_id" = "$container_id" ] \
    || fail "활성화할 Caddy 컨테이너가 하나가 아닙니다."
  if [ "$PREFLIGHT_MODE" = "running" ]; then
    [ "$container_id" = "$PREFLIGHT_CONTAINER_ID" ] \
      || fail "사전검사한 Caddy 컨테이너가 활성화 직전에 교체되었습니다."
  fi
  running_domain="$(read_running_domain .env)"
  [ "$running_domain" = "$PREFLIGHT_TARGET_DOMAIN" ] \
    || fail "활성화할 Caddy domain이 사전검사와 일치하지 않습니다."
  if [ "$PREFLIGHT_MODE" = "running" ]; then
    [ "$running_domain" = "$PREFLIGHT_RUNNING_DOMAIN" ] \
      || fail "사전검사한 Caddy domain이 활성화 직전에 변경되었습니다."
  fi
  container_hash="$(read_container_config_hash "$container_id")"
  [ "$container_hash" = "$PREFLIGHT_DESIRED_HASH" ] \
    || fail "활성화할 Caddy config hash가 사전검사와 일치하지 않습니다."
  desired_hash="$(read_desired_config_hash .env)"
  [ "$desired_hash" = "$PREFLIGHT_DESIRED_HASH" ] \
    || fail "활성화 직전 Caddy Compose service 설정이 변경되었습니다."
  [ "$(file_sha256 "$candidate_caddy")" = "$PREFLIGHT_CANDIDATE_SHA" ] \
    || fail "활성화 직전 Caddy 후보 파일이 변경되었습니다."
  [ "$(file_sha256 Caddyfile)" = "$PREFLIGHT_LIVE_SHA" ] \
    || fail "활성화 직전 현재 Caddyfile이 변경되었습니다."

  ACTIVATION_CADDY_ID="$container_id"
}

validate_running_config() {
  local config_path="$1"
  local domain="$2"
  local container_id="${3:-}"

  if [ -z "$container_id" ]; then
    container_id="$(running_caddy_id .env)"
  fi
  is_valid_container_id "$container_id" \
    || fail "Caddy 설정을 검증할 컨테이너를 하나로 식별하지 못했습니다."
  sudo docker exec -i \
    -e "SSING_DEV_DOMAIN=$domain" \
    "$container_id" \
    caddy validate --config - --adapter caddyfile \
    < "$config_path"
}

reload_running_config() {
  local config_path="$1"
  local domain="$2"
  local container_id="${3:-}"

  if [ -z "$container_id" ]; then
    container_id="$(running_caddy_id .env)"
  fi
  is_valid_container_id "$container_id" \
    || fail "Caddy 설정을 reload할 컨테이너를 하나로 식별하지 못했습니다."
  sudo docker exec -i \
    -e "SSING_DEV_DOMAIN=$domain" \
    "$container_id" \
    caddy reload --config - --adapter caddyfile \
    < "$config_path"
}

probe_app_route() {
  local domain="$1"
  local health_url
  local health_response

  if [ "$domain" = ":80" ]; then
    health_url="http://127.0.0.1/actuator/health"
  else
    health_url="https://${domain}/actuator/health"
  fi

  for attempt in {1..10}; do
    if [ "$domain" = ":80" ]; then
      health_response="$(curl -fsS --max-time 10 "$health_url" || true)"
    else
      health_response="$(curl -fsS --max-time 10 \
        --resolve "${domain}:443:127.0.0.1" \
        "$health_url" || true)"
    fi
    if grep -q '"status":"UP"' <<< "$health_response"; then
      return 0
    fi
    echo "복구한 Caddy app route 확인 시도: ${attempt}/10"
    sleep 2
  done

  fail "이전 Caddy 설정을 reload했지만 app route health를 확인하지 못했습니다."
}

rollback_activation() {
  local activation_id="$1"
  local candidate_caddy
  local rollback_caddy
  local rollback_domain_file
  local preflight_state
  local previous_domain
  local rollback_container_id

  candidate_caddy="$(candidate_path_for "$activation_id")"
  rollback_caddy="$(rollback_path_for "$activation_id")"
  rollback_domain_file="$(rollback_domain_path_for "$activation_id")"
  preflight_state="$(preflight_state_path_for "$activation_id")"

  [ -f "$rollback_caddy" ] \
    || fail "복구할 이전 Caddyfile이 없습니다: $rollback_caddy"
  [ -f "$rollback_domain_file" ] \
    || fail "복구할 이전 Caddy domain 정보가 없습니다: $rollback_domain_file"

  previous_domain="$(< "$rollback_domain_file")"
  is_valid_domain "$previous_domain" \
    || fail "복구할 이전 Caddy domain 형식이 올바르지 않습니다."
  [ -f .env ] || fail "Caddy 복구에 사용할 현재 .env가 없습니다."

  echo "이전 Caddy 설정 offline 검증"
  compose_with_env .env run --rm --no-deps -T \
    --entrypoint caddy \
    -e "SSING_DEV_DOMAIN=$previous_domain" \
    caddy validate --config - --adapter caddyfile \
    < "$rollback_caddy"

  # 복구 파일을 지우기 전에 live 파일과 실행 중 설정을 모두 이전 상태로 되돌린다.
  cp -f "$rollback_caddy" Caddyfile
  chmod 644 Caddyfile
  cmp -s "$rollback_caddy" Caddyfile \
    || fail "이전 Caddyfile을 live 파일로 복구하지 못했습니다."

  if [ -z "$(running_caddy_id)" ]; then
    echo "중지되거나 사라진 Caddy를 이전 live 설정으로 다시 기동합니다."
    compose_with_env .env up --pull never -d --no-deps caddy
  fi
  assert_running_caddy
  rollback_container_id="$(running_caddy_id)"
  is_valid_container_id "$rollback_container_id" \
    || fail "Caddy를 복구할 컨테이너를 하나로 식별하지 못했습니다."
  if [ "$(read_running_domain)" != "$previous_domain" ]; then
    fail "복구 기동한 Caddy domain이 이전 정상 설정과 일치하지 않습니다."
  fi

  echo "이전 Caddy 설정 running 검증"
  validate_running_config \
    "$rollback_caddy" \
    "$previous_domain" \
    "$rollback_container_id"
  [ "$(running_caddy_id)" = "$rollback_container_id" ] \
    || fail "복구 검증 중 Caddy 컨테이너가 교체되어 reload를 중단합니다."
  echo "이전 Caddy 설정 reload"
  reload_running_config \
    "$rollback_caddy" \
    "$previous_domain" \
    "$rollback_container_id"
  [ "$(running_caddy_id)" = "$rollback_container_id" ] \
    || fail "복구 reload 중 Caddy 컨테이너가 교체되어 확정을 중단합니다."
  probe_app_route "$previous_domain"

  rm -f "$MARKER_NAME"
  rm -f \
    "$candidate_caddy" \
    "$rollback_caddy" \
    "$rollback_domain_file" \
    "$preflight_state"
  echo "이전 Caddy 설정 복구 완료"
}

recover_pending_activation() {
  if [ ! -e "$MARKER_NAME" ]; then
    return 0
  fi

  local marker_id
  marker_id="$(read_marker_id)"
  echo "미완료 Caddy 반영 감지: $marker_id"
  rollback_activation "$marker_id"
}

preflight_candidate() {
  local candidate_caddy
  local preflight_state
  local bootstrap_live=0

  recover_pending_activation
  candidate_caddy="$(candidate_path_for "$CURRENT_ACTIVATION_ID")"
  preflight_state="$(preflight_state_path_for "$CURRENT_ACTIVATION_ID")"
  rm -f "$preflight_state" "${preflight_state}.tmp"
  [ -f "$candidate_caddy" ] \
    || fail "현재 run의 Caddy 후보 설정이 없습니다: $candidate_caddy"
  [ -f .env.next ] || fail "Caddy 후보 검증에 사용할 .env.next가 없습니다."

  # 파일 bind mount를 만들기 위한 빈 파일이며, 검증 실패 시 반드시 제거한다.
  if [ ! -s Caddyfile ]; then
    : > Caddyfile
    chmod 644 Caddyfile
    bootstrap_live=1
  fi

  echo "현재 run의 Caddy 후보 설정 사전 검증"
  if ! compose_with_env .env.next run --rm --no-deps -T \
      --entrypoint caddy \
      -e "SSING_DEV_DOMAIN=$SSING_DEV_DOMAIN" \
      caddy validate --config - --adapter caddyfile \
      < "$candidate_caddy"; then
    if [ "$bootstrap_live" -eq 1 ]; then
      rm -f Caddyfile
    fi
    fail "Caddy 후보 설정 사전 검증에 실패했습니다."
  fi

  if [ "$bootstrap_live" -eq 1 ]; then
    cp -f "$candidate_caddy" Caddyfile
    chmod 644 Caddyfile
    cmp -s "$candidate_caddy" Caddyfile \
      || fail "최초 Caddyfile을 검증한 후보로 준비하지 못했습니다."
  fi

  # DB 초기화 전에 확인한 Caddy runtime과 파일을 저장해 실제 reload 직전에 다시 대조한다.
  write_preflight_state "$candidate_caddy"
}

activate_candidate() {
  local candidate_caddy
  local rollback_caddy
  local rollback_domain_file
  local previous_domain

  recover_pending_activation
  candidate_caddy="$(candidate_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_caddy="$(rollback_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_domain_file="$(rollback_domain_path_for "$CURRENT_ACTIVATION_ID")"

  [ -f "$candidate_caddy" ] \
    || fail "현재 run의 Caddy 후보 설정이 없습니다: $candidate_caddy"
  [ -s Caddyfile ] || fail "복구 기준이 될 현재 Caddyfile이 없습니다."
  [ -f .env ] || fail "Caddy 반영에 사용할 승격된 .env가 없습니다."

  # 사전검사 뒤 app 배포 중 Caddy나 설정 파일이 바뀌었다면 기존 runtime을 건드리지 않는다.
  assert_preflight_state_unchanged "$candidate_caddy"

  # app 배포에서 Caddy를 제외하므로 최초 기동·중지 상태만 검증된 live 설정으로 명시적으로 시작한다.
  if [ -z "$(running_caddy_id)" ]; then
    compose_with_env .env up --pull never -d --no-deps caddy
  fi
  assert_running_caddy
  assert_running_caddy_matches_preflight "$candidate_caddy"

  previous_domain="$(read_running_domain)"
  is_valid_domain "$previous_domain" \
    || fail "실행 중 Caddy의 기존 domain 형식이 올바르지 않습니다."

  echo "현재 run의 Caddy 후보 설정 검증"
  validate_running_config \
    "$candidate_caddy" \
    "$SSING_DEV_DOMAIN" \
    "$ACTIVATION_CADDY_ID"

  # 검증 명령 중에도 runtime이 바뀌지 않았는지 reload 대상과 입력을 마지막으로 고정한다.
  assert_running_caddy_matches_preflight "$candidate_caddy"

  cp -p Caddyfile "$rollback_caddy"
  printf '%s\n' "$previous_domain" > "${rollback_domain_file}.tmp"
  mv -f "${rollback_domain_file}.tmp" "$rollback_domain_file"
  printf '%s\n' "$CURRENT_ACTIVATION_ID" > "${MARKER_NAME}.tmp"
  mv -f "${MARKER_NAME}.tmp" "$MARKER_NAME"

  # 두 smoke가 끝날 때까지 marker와 rollback 파일을 남겨 semantic 오류도 되돌릴 수 있게 한다.
  if ! cp -f "$candidate_caddy" Caddyfile \
      || ! chmod 644 Caddyfile \
      || ! cmp -s "$candidate_caddy" Caddyfile \
      || ! reload_running_config \
        "$candidate_caddy" \
        "$SSING_DEV_DOMAIN" \
        "$ACTIVATION_CADDY_ID"; then
    echo "Caddy 후보 반영 실패로 이전 설정 복구를 시도합니다."
    rollback_activation "$CURRENT_ACTIVATION_ID"
    fail "Caddy 후보 설정을 반영하지 못했습니다."
  fi

  echo "Caddy 후보 설정 반영 완료, smoke 결과 대기"
}

finalize_activation() {
  [ -e "$MARKER_NAME" ] || fail "확정할 Caddy 활성화 marker가 없습니다."

  local marker_id
  local candidate_caddy
  local rollback_caddy
  local rollback_domain_file
  local preflight_state
  marker_id="$(read_marker_id)"
  [ "$marker_id" = "$CURRENT_ACTIVATION_ID" ] \
    || fail "Caddy 활성화 marker가 현재 배포 run과 일치하지 않습니다."

  candidate_caddy="$(candidate_path_for "$marker_id")"
  rollback_caddy="$(rollback_path_for "$marker_id")"
  rollback_domain_file="$(rollback_domain_path_for "$marker_id")"
  preflight_state="$(preflight_state_path_for "$marker_id")"

  # marker를 먼저 제거하면 이후 파일 정리가 중단돼도 정상 설정을 다시 rollback하지 않는다.
  rm -f "$MARKER_NAME"
  rm -f \
    "$candidate_caddy" \
    "$rollback_caddy" \
    "$rollback_domain_file" \
    "$preflight_state"
  echo "Caddy 설정 확정 완료"
}

rollback_pending_activation() {
  if [ ! -e "$MARKER_NAME" ]; then
    echo "복구할 Caddy 활성화 transaction이 없습니다."
    return 0
  fi

  rollback_activation "$(read_marker_id)"
}

settle_activation() {
  case "${CADDY_SMOKE_SUCCEEDED:-}" in
    true)
      finalize_activation
      ;;
    false)
      rollback_pending_activation
      ;;
    *)
      fail "CADDY_SMOKE_SUCCEEDED는 true 또는 false여야 합니다."
      ;;
  esac
}

cleanup_current_artifacts() {
  local candidate_caddy
  local rollback_caddy
  local rollback_domain_file
  local preflight_state

  if [ -e "$MARKER_NAME" ]; then
    echo "미완료 Caddy transaction의 복구 파일을 보존합니다: $(read_marker_id)"
    return 0
  fi

  candidate_caddy="$(candidate_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_caddy="$(rollback_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_domain_file="$(rollback_domain_path_for "$CURRENT_ACTIVATION_ID")"
  preflight_state="$(preflight_state_path_for "$CURRENT_ACTIVATION_ID")"
  rm -f \
    "$candidate_caddy" \
    "$rollback_caddy" \
    "$rollback_domain_file" \
    "$preflight_state" \
    "${preflight_state}.tmp" \
    "${rollback_domain_file}.tmp" \
    "${MARKER_NAME}.tmp"
}

case "$COMMAND" in
  preflight|activate|settle|finalize|rollback|cleanup)
    ;;
  *)
    fail "사용법: $0 {preflight|activate|settle|finalize|rollback|cleanup}"
    ;;
esac

require_env \
  DEPLOY_DIR \
  COMPOSE_FILE \
  DEPLOY_SHA \
  DEPLOY_RUN_ID \
  DEPLOY_RUN_ATTEMPT \
  SSING_DEV_DOMAIN

is_valid_activation_id "${DEPLOY_SHA}-${DEPLOY_RUN_ID}-${DEPLOY_RUN_ATTEMPT}" \
  || fail "현재 배포 run 식별자 형식이 올바르지 않습니다."
is_valid_domain "$SSING_DEV_DOMAIN" \
  || fail "현재 배포 Caddy domain 형식이 올바르지 않습니다."

CURRENT_ACTIVATION_ID="${DEPLOY_SHA}-${DEPLOY_RUN_ID}-${DEPLOY_RUN_ATTEMPT}"
cd "$DEPLOY_DIR"

case "$COMMAND" in
  preflight)
    preflight_candidate
    ;;
  activate)
    activate_candidate
    ;;
  settle)
    settle_activation
    ;;
  finalize)
    finalize_activation
    ;;
  rollback)
    rollback_pending_activation
    ;;
  cleanup)
    cleanup_current_artifacts
    ;;
esac
