#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

GAME_REPOSITORY_URL="https://github.com/StarLoco/StarLoco-Game.git"
GAME_REPOSITORY_REF="v1.0.6"
GAME_IMAGE="starloco-offline/game:custom"
ROLLBACK_IMAGE="starloco-offline/game:rollback"

usage() {
  cat <<'HELP'
Développement du serveur de jeu dans ce même dossier.

Usage : ./game-dev.sh <commande> [options]

Commandes :
  init              Récupère le code dans ./serveur-jeu et crée une branche locale.
  bootstrap         Prépare toutes les sources, images et caches (connexion requise).
  path              Affiche le chemin du code à modifier.
  status            Affiche l'état Git, l'image et le conteneur.
  diff              Affiche les modifications locales du serveur.
  test              Compile et exécute les tests Gradle hors ligne.
  build             Compile et construit l'image sans redémarrer.
  deploy            Teste, construit et redéploie uniquement le serveur de jeu.
  rollback          Réactive automatiquement l'image précédente.
  restart           Redémarre le serveur de jeu sans recompiler.
  logs              Suit les journaux du serveur de jeu.
  shell             Ouvre un shell dans le conteneur du serveur.
  help              Affiche cette aide.

Cycle recommandé :
  ./game-dev.sh init
  # modifier les fichiers dans ./serveur-jeu/src
  ./game-dev.sh deploy
HELP
}

migrate_legacy_source() {
  if [[ ! -e "$GAME_SOURCE_PRIMARY/.git" && -d "$GAME_SOURCE_LEGACY/.git" ]]; then
    if [[ -d "$GAME_SOURCE_PRIMARY" ]] && [[ -n "$(find "$GAME_SOURCE_PRIMARY" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
      die "$GAME_SOURCE_PRIMARY n'est pas vide ; migration automatique impossible."
    fi
    rm -rf "$GAME_SOURCE_PRIMARY"
    mv "$GAME_SOURCE_LEGACY" "$GAME_SOURCE_PRIMARY"
    printf 'Anciennes sources déplacées vers %s\n' "$GAME_SOURCE_PRIMARY"
  fi
}

init_source() {
  command -v git >/dev/null 2>&1 || die "git est requis."
  migrate_legacy_source

  if [[ -d "$GAME_SOURCE_PRIMARY/.git" ]]; then
    printf 'Sources déjà prêtes : %s\n' "$GAME_SOURCE_PRIMARY"
    git -C "$GAME_SOURCE_PRIMARY" status --short --branch
    return
  fi

  if [[ -d "$GAME_SOURCE_PRIMARY" ]] && [[ -n "$(find "$GAME_SOURCE_PRIMARY" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    die "$GAME_SOURCE_PRIMARY doit être vide avant l'initialisation."
  fi

  rm -rf "$GAME_SOURCE_PRIMARY"
  git init -q "$GAME_SOURCE_PRIMARY"
  git -C "$GAME_SOURCE_PRIMARY" remote add origin "$GAME_REPOSITORY_URL"
  git -C "$GAME_SOURCE_PRIMARY" fetch --depth 1 origin "$GAME_REPOSITORY_REF"
  git -C "$GAME_SOURCE_PRIMARY" checkout -q -b "local-${GAME_REPOSITORY_REF}" FETCH_HEAD
  printf 'Code prêt dans : %s\n' "$GAME_SOURCE_PRIMARY"
  printf 'Branche locale : local-%s\n' "$GAME_REPOSITORY_REF"
}

ensure_build_images() {
  local gradle_image missing=()
  gradle_image="$(cat "$ROOT/manifests/GRADLE_IMAGE" 2>/dev/null || printf '%s' 'gradle:8.10.2-jdk21')"
  mapfile -t missing < <(missing_images "$gradle_image" starloco-offline/game:prepared)
  if (( ${#missing[@]} > 0 )); then
    load_bundle_images_if_present
    mapfile -t missing < <(missing_images "$gradle_image" starloco-offline/game:prepared)
  fi
  if (( ${#missing[@]} > 0 )); then
    printf 'Images nécessaires manquantes :\n' >&2
    printf '  - %s\n' "${missing[@]}" >&2
    die "Lancez ./game-dev.sh bootstrap sur une machine connectée."
  fi
}

show_status() {
  local repo
  repo="$(game_source_dir)"
  printf 'Code : %s\n' "$repo"
  if [[ -d "$repo/.git" ]]; then
    git -C "$repo" status --short --branch
    printf 'Révision : %s\n' "$(git -C "$repo" rev-parse --short=12 HEAD)"
  else
    printf 'Sources non initialisées.\n'
  fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    if image_exists "$GAME_IMAGE"; then
      printf 'Image active : %s\n' "$(docker image inspect --format '{{.Id}}' "$GAME_IMAGE")"
      docker image inspect --format 'Révision image : {{index .Config.Labels "org.opencontainers.image.revision"}} ; sources modifiées : {{index .Config.Labels "dev.starloco.source-dirty"}}' "$GAME_IMAGE" 2>/dev/null || true
    else
      printf 'Image active : absente\n'
    fi
    compose ps game || true
  else
    printf 'Docker indisponible : état du conteneur non affiché.\n'
  fi

  if [[ -s "$ROOT/runtime/builds/game-LAST_BUILD.txt" ]]; then
    printf '\nDernière construction :\n'
    sed 's/^/  /' "$ROOT/runtime/builds/game-LAST_BUILD.txt"
  fi
}

run_tests() {
  local repo gradle_image
  need_docker
  repo="$(require_game_source)"
  ensure_build_images
  gradle_image="$(cat "$ROOT/manifests/GRADLE_IMAGE" 2>/dev/null || printf '%s' 'gradle:8.10.2-jdk21')"
  mkdir -p "$ROOT/cache/gradle"
  docker run --rm --network none \
    -e GRADLE_USER_HOME=/gradle-cache \
    -v "$repo:/src" \
    -v "$ROOT/build-contexts/game:/build-contexts/game" \
    -v "$ROOT/cache/gradle:/gradle-cache" \
    -w /src "$gradle_image" \
    gradle --offline --no-daemon --stacktrace clean check jar
}

backup_current_image() {
  if image_exists "$GAME_IMAGE"; then
    docker tag "$GAME_IMAGE" "$ROLLBACK_IMAGE"
    printf 'Image précédente sauvegardée : %s\n' "$ROLLBACK_IMAGE"
  fi
}

service_is_running() {
  compose ps --services --status running 2>/dev/null | grep -qx game
}

rollback_image() {
  need_docker
  image_exists "$ROLLBACK_IMAGE" || die "Aucune image de retour arrière n'est disponible."
  docker tag "$ROLLBACK_IMAGE" "$GAME_IMAGE"
  if service_is_running; then
    compose up -d --no-build --pull never --force-recreate game
    if wait_for_service_health game 90; then
      printf 'Retour arrière réussi.\n'
    else
      compose logs --tail=150 game >&2 || true
      die "L'ancienne image n'est pas redevenue saine."
    fi
  else
    printf 'Image précédente restaurée. La pile n’est pas démarrée.\n'
  fi
}

deploy_game() {
  need_docker
  require_game_source >/dev/null
  ensure_build_images
  backup_current_image

  "$ROOT/offline-rebuild.sh" game --run-tests --no-restart

  if service_is_running; then
    "$ROOT/offline-migrate.sh" --guild-features-only
    compose up -d --no-build --pull never --force-recreate game
    printf 'Attente du healthcheck du nouveau serveur…\n'
    if wait_for_service_health game 90; then
      printf 'Déploiement réussi.\n'
      compose ps game
      return
    fi

    printf 'Le nouveau serveur n’est pas sain. Journaux récents :\n' >&2
    compose logs --tail=150 game >&2 || true
    if image_exists "$ROLLBACK_IMAGE"; then
      printf 'Retour automatique à l’image précédente…\n' >&2
      docker tag "$ROLLBACK_IMAGE" "$GAME_IMAGE"
      compose up -d --no-build --pull never --force-recreate game
      wait_for_service_health game 90 || true
    fi
    die "Déploiement annulé ; l'image précédente a été restaurée lorsqu'elle était disponible."
  else
    printf 'Image construite. La pile n’était pas démarrée ; lancez ./offline-start.sh.\n'
  fi
}

command="${1:-help}"
shift || true
case "$command" in
  init) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh init"; init_source ;;
  bootstrap) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh bootstrap"; "$ROOT/prepare-online.sh" ;;
  path) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh path"; game_source_dir ;;
  status) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh status"; show_status ;;
  diff)
    [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh diff"
    repo="$(require_game_source)"
    git -C "$repo" status --short --branch
    git -C "$repo" diff --stat
    git -C "$repo" diff
    ;;
  test) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh test"; run_tests ;;
  build)
    [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh build"
    need_docker; require_game_source >/dev/null; ensure_build_images
    "$ROOT/offline-rebuild.sh" game --no-restart
    ;;
  deploy|redeploy) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh deploy"; deploy_game ;;
  rollback) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh rollback"; rollback_image ;;
  restart)
    [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh restart"
    need_docker
    compose restart game
    wait_for_service_health game 90 || die "Le serveur n'est pas sain après redémarrage."
    compose ps game
    ;;
  logs) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh logs"; need_docker; compose logs -f --tail=200 game ;;
  shell) [[ $# -eq 0 ]] || die "Usage : ./game-dev.sh shell"; need_docker; compose exec game sh ;;
  help|-h|--help) usage ;;
  *) usage >&2; die "Commande inconnue : $command" ;;
esac
