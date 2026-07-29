#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"
need_docker
compose ps --services --status running | grep -qx web ||
  die "Le portail n’est pas démarré."
compose exec web php /src/public/cli-create-account.php
