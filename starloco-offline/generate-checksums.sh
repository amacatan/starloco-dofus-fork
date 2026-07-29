#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p checksums
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

find . -type f \
  ! -path './checksums/SHA256SUMS' \
  ! -path './runtime/stack/.env' \
  ! -path './runtime/stack/secrets/*' \
  ! -path './sources/*' \
  ! -path './serveur-jeu/*' \
  ! -path './runtime/builds/*' \
  ! -path './cache/*' \
  ! -path './docker-images/*' \
  ! -path './git-bundles/*' \
  ! -path './client/*' \
  ! -path './.upstream/*' \
  -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$tmp"

mv "$tmp" checksums/SHA256SUMS
trap - EXIT
printf 'Sommes mises à jour : %s\n' "$ROOT/checksums/SHA256SUMS"
