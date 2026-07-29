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
grep -q "! -path './serveur-jeu/\*'" "$ROOT/generate-checksums.sh"
grep -q 'org.opencontainers.image.revision' "$ROOT/build-contexts/game/Dockerfile"
printf 'Workflow développeur : contrôles statiques réussis.\n'
