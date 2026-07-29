#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

RESET=false
case "${1:-}" in
  "") ;;
  --reset) RESET=true ;;
  -h|--help)
    printf 'Usage : %s [--reset]\n' "$0"
    exit 0
    ;;
  *) die "Usage : $0 [--reset]" ;;
esac

ensure_runtime_files
secret_path="$STACK/secrets/admin_password.secret"

if $RESET; then
  random_hex 16 > "$secret_path"
  chmod 600 "$secret_path"
  printf 'Mot de passe administrateur renouvelé.\n'

  if command -v docker >/dev/null 2>&1 \
     && docker info >/dev/null 2>&1 \
     && compose ps --services --status running 2>/dev/null | grep -qx web; then
    compose up -d --no-build --pull never --no-deps --force-recreate web
    wait_for_service_health web 60 ||
      die "Le portail n’est pas redevenu sain après rotation du secret."
  fi
fi

username="$(grep -E '^ADMIN_USERNAME=' "$STACK/.env" | cut -d= -f2-)"
[[ -n "$username" ]] || username="admin"

printf 'URL          : http://%s/admin/\n' "$(grep -E '^BIND_ADDRESS=' "$STACK/.env" | cut -d= -f2-)"
printf 'Identifiant  : %s\n' "$username"
printf 'Mot de passe : %s\n' "$(<"$secret_path")"

