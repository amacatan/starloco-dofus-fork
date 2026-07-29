#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

need_docker
ensure_runtime_files

required=(mariadb:11.3 starloco-offline/web:prepared)
mapfile -t missing < <(missing_images "${required[@]}")
if (( ${#missing[@]} > 0 )); then
  load_bundle_images_if_present
  mapfile -t missing < <(missing_images "${required[@]}")
fi
if (( ${#missing[@]} > 0 )); then
  printf 'Images Docker manquantes :\n' >&2
  printf '  - %s\n' "${missing[@]}" >&2
  die "Le portail doit d’abord être préparé avec ./game-dev.sh bootstrap."
fi

printf 'Démarrage de MariaDB et du portail…\n'
compose up -d --no-build --pull never mariadb web
wait_for_mariadb

printf 'Installation des tables du fil communautaire…\n'
"$ROOT/offline-migrate.sh" --social-only

printf 'Activation du nouveau portail et du collecteur…\n'
compose up -d --no-build --pull never --force-recreate web social-worker

for _ in $(seq 1 30); do
  if compose exec -T web curl -fsS 'http://127.0.0.1/health.php?deep=1' >/dev/null 2>&1 &&
     compose exec -T social-worker php /src/public/cli-social-health.php >/dev/null 2>&1; then
    bind_address="$(grep -E '^BIND_ADDRESS=' "$STACK/.env" | cut -d= -f2)"
    printf '\nFil communautaire activé : http://%s/\n' "$bind_address"
    printf 'Création de compte : http://%s/register.php\n' "$bind_address"
    exit 0
  fi
  sleep 3
done

die "Le portail a été redéployé mais le contrôle de santé n’est pas encore positif. Consultez ./offline-logs.sh web et ./offline-logs.sh social-worker."
