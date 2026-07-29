#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"
need_docker
service="${1:-}"
if [[ -n "$service" ]]; then
  compose config --services | grep -qx "$service" ||
    die "Service inconnu : $service"
  compose logs -f --tail=200 "$service"
else
  compose logs -f --tail=200
fi
