#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"
need_docker
[[ -s "$IMAGE_BUNDLE" ]] || die "Archive d’images absente : $IMAGE_BUNDLE"
load_bundle_images_if_present
