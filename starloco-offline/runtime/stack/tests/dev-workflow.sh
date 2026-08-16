#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"

bash -n "$ROOT/game-dev.sh"
bash -n "$ROOT/offline-rebuild.sh"
grep -q 'GAME_SOURCE_PRIMARY="$ROOT/serveur-jeu"' "$ROOT/offline-common.sh"
grep -q -- '--run-tests' "$ROOT/offline-rebuild.sh"
grep -q 'gradle_tasks=(clean check jar)' "$ROOT/offline-rebuild.sh"
grep -q 'clean check jar' "$ROOT/game-dev.sh"
grep -q 'ROLLBACK_IMAGE=' "$ROOT/game-dev.sh"
grep -q 'wait_for_service_health game' "$ROOT/game-dev.sh"
grep -q 'wait_for_bind_address "$bind_address"' "$ROOT/offline-start.sh"
grep -q "! -path './serveur-jeu/\*'" "$ROOT/generate-checksums.sh"
grep -q 'org.opencontainers.image.revision' "$ROOT/build-contexts/game/Dockerfile"

source "$ROOT/offline-common.sh"
ip() {
  printf '%s\n' \
    '1: lo    inet 127.0.0.1/8 scope host lo' \
    '2: eth0  inet 192.0.2.10/24 scope global eth0'
}
bind_address_available 127.0.0.1
bind_address_available 192.0.2.10
bind_address_available 0.0.0.0
if bind_address_available 192.0.2.11; then
  printf 'Une adresse absente a été acceptée comme BIND_ADDRESS.\n' >&2
  exit 1
fi

printf 'Workflow développeur : contrôles statiques réussis.\n'
