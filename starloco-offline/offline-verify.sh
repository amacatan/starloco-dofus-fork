#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

[[ -s checksums/SHA256SUMS ]] || {
  echo "Fichier de sommes absent." >&2
  exit 1
}
sha256sum -c checksums/SHA256SUMS
"$ROOT/offline-doctor.sh"
echo "Intégrité et tests statiques validés."
