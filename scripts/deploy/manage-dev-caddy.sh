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

assert_caddy_recreate_not_needed() {
  local container_id
  local running_domain
  local current_hash
  local desired_hash_line
  local desired_hash

  container_id="$(running_caddy_id .env.next)"
  if [ -z "$container_id" ]; then
    echo "실행 중인 Caddy가 없어 최초 기동 또는 명시적 복구 경로로 진행합니다."
    return 0
  fi

  running_domain="$(read_running_domain .env.next)"
  is_valid_domain "$running_domain" \
    || fail "실행 중 Caddy의 domain 형식이 올바르지 않습니다."
  if [ "$running_domain" != "$SSING_DEV_DOMAIN" ]; then
    fail "Caddy domain 변경은 무중단 Caddyfile reload와 분리해야 합니다."
  fi

  current_hash="$(sudo docker inspect \
    --format '{{ index .Config.Labels "com.docker.compose.config-hash" }}' \
    "$container_id")"
  desired_hash_line="$(compose_with_env .env.next config --hash caddy)"
  desired_hash="${desired_hash_line##* }"
  if [[ ! "$current_hash" =~ ^[0-9a-f]{64}$ \
      || ! "$desired_hash" =~ ^[0-9a-f]{64}$ ]]; then
    fail "Caddy Compose config hash를 안전하게 비교하지 못했습니다."
  fi
  if [ "$current_hash" != "$desired_hash" ]; then
    fail "Caddy service 설정 변경은 app 배포와 분리된 명시적 전환이 필요합니다."
  fi
}

validate_running_config() {
  local config_path="$1"
  local domain="$2"
  compose_with_env .env exec -T \
    -e "SSING_DEV_DOMAIN=$domain" \
    caddy caddy validate --config - --adapter caddyfile \
    < "$config_path"
}

reload_running_config() {
  local config_path="$1"
  local domain="$2"
  compose_with_env .env exec -T \
    -e "SSING_DEV_DOMAIN=$domain" \
    caddy caddy reload --config - --adapter caddyfile \
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
  local previous_domain

  candidate_caddy="$(candidate_path_for "$activation_id")"
  rollback_caddy="$(rollback_path_for "$activation_id")"
  rollback_domain_file="$(rollback_domain_path_for "$activation_id")"

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
  if [ "$(read_running_domain)" != "$previous_domain" ]; then
    fail "복구 기동한 Caddy domain이 이전 정상 설정과 일치하지 않습니다."
  fi

  echo "이전 Caddy 설정 running 검증"
  validate_running_config "$rollback_caddy" "$previous_domain"
  echo "이전 Caddy 설정 reload"
  reload_running_config "$rollback_caddy" "$previous_domain"
  probe_app_route "$previous_domain"

  rm -f "$MARKER_NAME"
  rm -f "$candidate_caddy" "$rollback_caddy" "$rollback_domain_file"
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
  local bootstrap_live=0

  recover_pending_activation
  candidate_caddy="$(candidate_path_for "$CURRENT_ACTIVATION_ID")"
  [ -f "$candidate_caddy" ] \
    || fail "현재 run의 Caddy 후보 설정이 없습니다: $candidate_caddy"
  [ -f .env.next ] || fail "Caddy 후보 검증에 사용할 .env.next가 없습니다."
  assert_caddy_recreate_not_needed

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

  # app 배포에서 Caddy를 제외하므로 최초 기동·중지 상태만 검증된 live 설정으로 명시적으로 시작한다.
  if [ -z "$(running_caddy_id)" ]; then
    compose_with_env .env up --pull never -d --no-deps caddy
  fi
  assert_running_caddy

  previous_domain="$(read_running_domain)"
  is_valid_domain "$previous_domain" \
    || fail "실행 중 Caddy의 기존 domain 형식이 올바르지 않습니다."

  echo "현재 run의 Caddy 후보 설정 검증"
  validate_running_config "$candidate_caddy" "$SSING_DEV_DOMAIN"

  cp -p Caddyfile "$rollback_caddy"
  printf '%s\n' "$previous_domain" > "${rollback_domain_file}.tmp"
  mv -f "${rollback_domain_file}.tmp" "$rollback_domain_file"
  printf '%s\n' "$CURRENT_ACTIVATION_ID" > "${MARKER_NAME}.tmp"
  mv -f "${MARKER_NAME}.tmp" "$MARKER_NAME"

  # 두 smoke가 끝날 때까지 marker와 rollback 파일을 남겨 semantic 오류도 되돌릴 수 있게 한다.
  if ! cp -f "$candidate_caddy" Caddyfile \
      || ! chmod 644 Caddyfile \
      || ! cmp -s "$candidate_caddy" Caddyfile \
      || ! reload_running_config "$candidate_caddy" "$SSING_DEV_DOMAIN"; then
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
  marker_id="$(read_marker_id)"
  [ "$marker_id" = "$CURRENT_ACTIVATION_ID" ] \
    || fail "Caddy 활성화 marker가 현재 배포 run과 일치하지 않습니다."

  candidate_caddy="$(candidate_path_for "$marker_id")"
  rollback_caddy="$(rollback_path_for "$marker_id")"
  rollback_domain_file="$(rollback_domain_path_for "$marker_id")"

  # marker를 먼저 제거하면 이후 파일 정리가 중단돼도 정상 설정을 다시 rollback하지 않는다.
  rm -f "$MARKER_NAME"
  rm -f "$candidate_caddy" "$rollback_caddy" "$rollback_domain_file"
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

  if [ -e "$MARKER_NAME" ]; then
    echo "미완료 Caddy transaction의 복구 파일을 보존합니다: $(read_marker_id)"
    return 0
  fi

  candidate_caddy="$(candidate_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_caddy="$(rollback_path_for "$CURRENT_ACTIVATION_ID")"
  rollback_domain_file="$(rollback_domain_path_for "$CURRENT_ACTIVATION_ID")"
  rm -f \
    "$candidate_caddy" \
    "$rollback_caddy" \
    "$rollback_domain_file" \
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
