#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

MODE="full"
case "${1:-}" in
  "") ;;
  --portal-only) MODE="portal" ;;
  *) die "Usage : $0 [--portal-only]" ;;
esac

need_docker
ensure_runtime_files

base_images=(mariadb:11.3 starloco-offline/web:prepared)
full_images=(redis:7-alpine starloco-offline/login:custom starloco-offline/game:custom)
required=("${base_images[@]}")
[[ "$MODE" == "full" ]] && required+=("${full_images[@]}")

mapfile -t missing < <(missing_images "${required[@]}")
if (( ${#missing[@]} > 0 )); then
  load_bundle_images_if_present
  mapfile -t missing < <(missing_images "${required[@]}")
fi
if (( ${#missing[@]} > 0 )); then
  printf 'Images Docker manquantes :\n' >&2
  printf '  - %s\n' "${missing[@]}" >&2
  die "Exécutez ./prepare-online.sh sur une machine connectée, ou ajoutez docker-images/starloco-images.tar."
fi

game_sql_paths=()
for sql_file in "${GAME_DB_INIT_FILES[@]}"; do
  game_sql_paths+=("$STACK/db-init/$sql_file")
done
require_paths "${game_sql_paths[@]}"
[[ $(stat -c '%s' "$STACK/db-init/04-game.sql") -gt 50000000 ]] ||
  die "04-game.sql paraît tronqué."

if [[ "$MODE" == "full" ]]; then
  foundation_services=(mariadb redis)
  application_services=(login game web social-worker)
else
  foundation_services=(mariadb)
  application_services=(web social-worker)
fi

wait_for_services() {
  local service
  for service in "$@"; do
    printf 'Attente du service %s…\n' "$service"
    if ! wait_for_service_health "$service" 90; then
      compose logs --tail=120 "$service" >&2 || true
      die "Le service $service n’est pas devenu sain."
    fi
  done
}

printf 'Arrêt propre des services applicatifs avant migration…\n'
compose stop --timeout 30 "${application_services[@]}"

printf 'Démarrage du socle de données…\n'
compose up -d --no-build --pull never --remove-orphans "${foundation_services[@]}"
wait_for_mariadb
if [[ "$MODE" == "full" ]]; then
  wait_for_services redis
fi

printf 'Application des migrations avant ouverture des services…\n'
if [[ "$MODE" == "full" ]]; then
  "$ROOT/offline-migrate.sh"
else
  "$ROOT/offline-migrate.sh" --social-only
fi

printf 'Recréation et démarrage des services applicatifs…\n'
compose up -d --no-build --pull never --no-deps --force-recreate "${application_services[@]}"
wait_for_services "${application_services[@]}"

compose ps
bind_address="$(grep -E '^BIND_ADDRESS=' "$STACK/.env" | cut -d= -f2)"
printf '\nFil communautaire : http://%s/\n' "$bind_address"
printf 'Annuaire des joueurs : http://%s/players\n' "$bind_address"
printf 'Création de compte : http://%s/register.php\n' "$bind_address"
printf 'Administration : http://%s/admin/ (mot de passe : ./offline-admin-password.sh)\n' "$bind_address"
