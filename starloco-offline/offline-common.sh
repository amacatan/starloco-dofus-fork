#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
STACK="$ROOT/runtime/stack"
OVERRIDE="$ROOT/docker-compose.offline.yml"
GAME_SOURCE_PRIMARY="$ROOT/serveur-jeu"
GAME_SOURCE_LEGACY="$ROOT/sources/StarLoco-Game"
IMAGE_BUNDLE="$ROOT/docker-images/starloco-images.tar"
IMAGE_BUNDLE_CHECKSUM="$ROOT/docker-images/starloco-images.tar.sha256"
GAME_DB_INIT_FILES=(
  "04-game.sql"
  "05-update_game_16.04.23.sql"
  "06-update_game_23.04.23.sql"
  "07-update_game_24.04.23.sql"
  "08-update_game_08.05.23.sql"
  "09-update_game_10.03.2024.sql"
  "10-update_game_pet_epo.sql"
)


die() {
  printf 'Erreur : %s\n' "$*" >&2
  exit 1
}

random_hex() {
  local bytes="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes"
  else
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

game_source_dir() {
  if [[ -d "$GAME_SOURCE_PRIMARY/.git" || -f "$GAME_SOURCE_PRIMARY/build.gradle" ]]; then
    printf '%s\n' "$GAME_SOURCE_PRIMARY"
  elif [[ -d "$GAME_SOURCE_LEGACY/.git" || -f "$GAME_SOURCE_LEGACY/build.gradle" ]]; then
    printf '%s\n' "$GAME_SOURCE_LEGACY"
  else
    printf '%s\n' "$GAME_SOURCE_PRIMARY"
  fi
}

require_game_source() {
  local source_dir
  source_dir="$(game_source_dir)"
  [[ -f "$source_dir/build.gradle" ]] ||
    die "Sources du serveur absentes. Lancez ./game-dev.sh init (ou ./prepare-online.sh)."
  printf '%s\n' "$source_dir"
}

ensure_runtime_files() {
  mkdir -p "$STACK/secrets"

  if [[ ! -f "$STACK/.env" ]]; then
    cp "$STACK/.env.example" "$STACK/.env"
    local game_key
    game_key="$(random_hex 32)"
    sed -i "s/^GAME_SERVER_KEY=.*/GAME_SERVER_KEY=$game_key/" "$STACK/.env"
    printf 'Configuration créée : %s\n' "$STACK/.env"
  fi

  local secret
  for secret in mariadb_root starloco_db_password exchange_key admin_password admin_hmac_key; do
    local path="$STACK/secrets/${secret}.secret"
    if [[ ! -s "$path" ]]; then
      if [[ "$secret" == "admin_password" ]]; then
        random_hex 16 > "$path"
      else
        random_hex 32 > "$path"
      fi
      printf 'Secret créé : %s\n' "$path"
    fi
    chmod 600 "$path"
  done
  chmod 600 "$STACK/.env"
}

compose() {
  ensure_runtime_files
  (cd "$STACK" && docker compose -f docker-compose.yml -f "$OVERRIDE" --env-file .env "$@")
}

need_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker est absent."
  docker compose version >/dev/null 2>&1 || die "Le plugin docker compose est absent."
  docker info >/dev/null 2>&1 || die "Le démon Docker ne répond pas."
}

bind_address_available() {
  local address="$1"

  case "$address" in
    0.0.0.0|::|'[::]') return 0 ;;
  esac

  command -v ip >/dev/null 2>&1 || return 2
  ip -o address show 2>/dev/null | awk -v expected="$address" '
    {
      split($4, current, "/")
      if (current[1] == expected) {
        found = 1
      }
    }
    END { exit(found ? 0 : 1) }
  '
}

wait_for_bind_address() {
  local address="$1" retries="${2:-30}" attempt result

  [[ -n "$address" ]] || die "BIND_ADDRESS est absent de $STACK/.env."
  for ((attempt=1; attempt<=retries; attempt++)); do
    if bind_address_available "$address"; then
      return 0
    else
      result=$?
    fi

    (( result != 2 )) || die "La commande 'ip' est absente ; impossible de valider BIND_ADDRESS."
    if (( attempt == 1 )); then
      printf 'Attente de l’adresse réseau %s…\n' "$address"
    fi
    (( attempt == retries )) || sleep 1
  done

  die "L’adresse BIND_ADDRESS=$address n’est attribuée à aucune interface locale. Vérifiez l’interface réseau ou corrigez $STACK/.env ; aucun service n’a été arrêté."
}

image_exists() {
  docker image inspect "$1" >/dev/null 2>&1
}

missing_images() {
  local image
  for image in "$@"; do
    image_exists "$image" || printf '%s\n' "$image"
  done
}

load_bundle_images_if_present() {
  [[ -f "$IMAGE_BUNDLE" ]] || return 0
  [[ -s "$IMAGE_BUNDLE_CHECKSUM" ]] ||
    die "Somme de contrôle absente : $IMAGE_BUNDLE_CHECKSUM"
  verify_image_bundle_checksum ||
    die "L’archive d’images est corrompue ou ne correspond pas à sa somme de contrôle."
  printf 'Chargement des images locales…\n'
  docker load -i "$IMAGE_BUNDLE"
}

verify_image_bundle_checksum() {
  [[ -s "$IMAGE_BUNDLE" && -s "$IMAGE_BUNDLE_CHECKSUM" ]] || return 1
  (cd "$ROOT" && sha256sum -c "docker-images/$(basename "$IMAGE_BUNDLE_CHECKSUM")" >/dev/null 2>&1)
}

wait_for_mariadb() {
  local retries="${1:-300}"
  local i
  for ((i=1; i<=retries; i++)); do
    if compose exec -T mariadb healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  die "MariaDB n’est pas devenue disponible."
}

wait_for_service_health() {
  local service="$1" retries="${2:-90}" container_id health i
  for ((i=1; i<=retries; i++)); do
    container_id="$(compose ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      case "$health" in
        healthy|running) return 0 ;;
        unhealthy|exited|dead) return 1 ;;
      esac
    fi
    sleep 2
  done
  return 1
}

require_paths() {
  local missing=0 path
  for path in "$@"; do
    if [[ ! -e "$path" ]]; then
      printf 'Élément manquant : %s\n' "$path" >&2
      missing=1
    fi
  done
  (( missing == 0 )) || exit 1
}
