#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"
need_docker
ensure_runtime_files

TARGET="all"
NO_RESTART=false
TARGET_SET=false

for arg in "$@"; do
  case "$arg" in
    game|login|all)
      [[ "$TARGET_SET" == false ]] || die "Une seule cible peut être indiquée."
      TARGET="$arg"
      TARGET_SET=true
      ;;
    --no-restart) NO_RESTART=true ;;
    # Compatibilité avec les anciennes commandes : les vérifications sont
    # désormais systématiques, avec ou sans cette option.
    --run-tests) ;;
    -h|--help)
      echo "Usage: $0 [game|login|all] [--run-tests] [--no-restart]"
      exit 0
      ;;
    *) die "Option inconnue : $arg" ;;
  esac
done

GRADLE_IMAGE="$(cat "$ROOT/manifests/GRADLE_IMAGE" 2>/dev/null || printf '%s' 'gradle:8.10.2-jdk21')"
CACHE="$ROOT/cache/gradle"
mkdir -p "$CACHE" "$ROOT/runtime/builds"

sync_without_git() {
  local src="$1" dst="$2"
  [[ -d "$src" ]] || die "Sources absentes : $src. Lancez ./prepare-online.sh."
  rm -rf "$dst"
  mkdir -p "$dst"
  tar -C "$src" --exclude='./.git' --exclude='./build' -cf - . | tar -C "$dst" -xf -
}

find_jar() {
  local repo="$1" preferred="$2"
  if [[ -f "$repo/build/libs/$preferred" ]]; then
    printf '%s\n' "$repo/build/libs/$preferred"
    return
  fi
  find "$repo/build/libs" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-plain.jar' \
    | sort | head -n1
}

source_revision() {
  local repo="$1"
  if [[ -d "$repo/.git" ]]; then
    git -C "$repo" rev-parse --short=12 HEAD 2>/dev/null || printf 'inconnu'
  else
    printf 'sans-git'
  fi
}

source_dirty() {
  local repo="$1"
  if [[ -d "$repo/.git" ]] && [[ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

record_build() {
  local name="$1" repo="$2" image="$3" revision="$4" dirty="$5" created="$6"
  cat > "$ROOT/runtime/builds/${name}-LAST_BUILD.txt" <<INFO
service=$name
source=$repo
revision=$revision
dirty=$dirty
image=$image
built_at=$created
INFO
}

build_java() {
  local name="$1" repo="$2" preferred="$3" context="$4" image="$5"
  local revision dirty created jar
  local -a gradle_tasks=(clean check jar)

  echo "Recompilation hors ligne de $name…"
  docker run --rm --network none \
    -e GRADLE_USER_HOME=/gradle-cache \
    -v "$repo:/src" \
    -v "$CACHE:/gradle-cache" \
    -w /src \
    "$GRADLE_IMAGE" \
    gradle --offline --no-daemon --stacktrace "${gradle_tasks[@]}"

  jar="$(find_jar "$repo" "$preferred")"
  [[ -n "$jar" && -f "$jar" ]] || die "JAR produit introuvable pour $name."
  cp "$jar" "$context/$preferred"

  revision="$(source_revision "$repo")"
  dirty="$(source_dirty "$repo")"
  created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  docker build --network=none \
    --build-arg BUILD_DATE="$created" \
    --build-arg SOURCE_REVISION="$revision" \
    --build-arg SOURCE_DIRTY="$dirty" \
    -t "$image" "$context"
  record_build "$name" "$repo" "$image" "$revision" "$dirty" "$created"
}

rebuild_game() {
  local context="$ROOT/build-contexts/game" repo
  repo="$(require_game_source)"
  sync_without_git "$repo/scripts" "$context/scripts"
  cp "$ROOT/sources/starloco-docker/game/entrypoint.sh" "$context/entrypoint.sh"
  build_java game "$repo" game.jar "$context" starloco-offline/game:custom
}

rebuild_login() {
  cp "$ROOT/sources/starloco-docker/login/entrypoint.sh" "$ROOT/build-contexts/login/entrypoint.sh"
  build_java login "$ROOT/sources/StarLoco-Login" login.jar "$ROOT/build-contexts/login" starloco-offline/login:custom
}

case "$TARGET" in
  game) rebuild_game ;;
  login) rebuild_login ;;
  all) rebuild_game; rebuild_login ;;
esac

if [[ "$NO_RESTART" == false ]]; then
  case "$TARGET" in
    all) services=(login game) ;;
    *) services=("$TARGET") ;;
  esac
  if compose ps --services --status running | grep -q .; then
    if [[ "$TARGET" == game || "$TARGET" == all ]]; then
      "$ROOT/offline-migrate.sh" --guild-features-only
    fi
    compose up -d --no-build --pull never --force-recreate "${services[@]}"
    compose ps
  else
    echo "Images reconstruites. La pile n'était pas démarrée."
  fi
fi
