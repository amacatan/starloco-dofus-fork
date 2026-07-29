#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"
need_docker
ensure_runtime_files
command -v git >/dev/null 2>&1 || die "git est requis."

mkdir -p "$ROOT/sources" "$ROOT/serveur-jeu" "$ROOT/cache/gradle" "$ROOT/docker-images" "$ROOT/manifests"

clone_ref() {
  local url="$1" ref="$2" destination="$3"
  local origin_url requested_commit current_commit
  if [[ -d "$destination/.git" ]]; then
    origin_url="$(git -C "$destination" remote get-url origin 2>/dev/null || true)"
    [[ "$origin_url" == "$url" ]] ||
      die "Dépôt incohérent dans $destination : origin vaut '${origin_url:-absent}', attendu '$url'. Arborescence conservée."

    git -C "$destination" fetch --depth 1 --filter=blob:none origin "$ref"
    requested_commit="$(git -C "$destination" rev-parse --verify 'FETCH_HEAD^{commit}')"
    current_commit="$(git -C "$destination" rev-parse --verify 'HEAD^{commit}')"
    if [[ "$ref" =~ ^[0-9a-fA-F]{40}$ ]] &&
       [[ "${requested_commit,,}" != "${ref,,}" ]]; then
      die "Référence incohérente pour $destination : '$ref' a fourni '$requested_commit'. Arborescence conservée."
    fi
    if ! git -C "$destination" merge-base --is-ancestor "$requested_commit" "$current_commit"; then
      die "Référence incohérente pour $destination : HEAD $current_commit n'est pas basé sur $ref ($requested_commit). Aucun reset effectué."
    fi
    printf 'Sources vérifiées, modifications locales conservées : %s (%s)\n' "$destination" "$current_commit"
    return
  fi
  if [[ -d "$destination" ]] && [[ -n "$(find "$destination" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    die "Le dossier $destination contient des fichiers sans dépôt Git ; aucun fichier ne sera écrasé."
  fi
  rm -rf "$destination"
  git init -q "$destination"
  git -C "$destination" remote add origin "$url"
  git -C "$destination" fetch --depth 1 --filter=blob:none origin "$ref"
  git -C "$destination" checkout -q --detach FETCH_HEAD
}

if [[ -d "$ROOT/sources/StarLoco-Game/.git" && ! -d "$ROOT/serveur-jeu/.git" ]]; then
  rm -rf "$ROOT/serveur-jeu"
  mv "$ROOT/sources/StarLoco-Game" "$ROOT/serveur-jeu"
fi
clone_ref https://github.com/StarLoco/StarLoco-Game.git v1.0.6 "$ROOT/serveur-jeu"
if git -C "$ROOT/serveur-jeu" symbolic-ref -q HEAD >/dev/null 2>&1; then
  :
else
  git -C "$ROOT/serveur-jeu" switch -c local-v1.0.6 >/dev/null 2>&1 || true
fi
clone_ref https://github.com/tiboitel/StarLoco-Login.git v1.0.3 "$ROOT/sources/StarLoco-Login"
clone_ref https://github.com/tiboitel/starloco-docker.git 44114333c8a900ad4ad3f64edfe1c03e603316c1 "$ROOT/sources/starloco-docker"

printf 'Synchronisation de la base et des correctifs SQL du jeu…\n'
for sql_file in "${GAME_DB_INIT_FILES[@]}"; do
  source_sql="$ROOT/serveur-jeu/db-init/$sql_file"
  [[ -s "$source_sql" ]] || die "Correctif SQL du jeu absent : $source_sql"
  cp -- "$source_sql" "$STACK/db-init/$sql_file"
done

{
  printf 'nom\turl\tréférence demandée\tcommit conservé\n'
  for spec in \
    "StarLoco-Game|https://github.com/StarLoco/StarLoco-Game.git|v1.0.6|$ROOT/serveur-jeu" \
    "StarLoco-Login|https://github.com/tiboitel/StarLoco-Login.git|v1.0.3|$ROOT/sources/StarLoco-Login" \
    "starloco-docker|https://github.com/tiboitel/starloco-docker.git|44114333c8a900ad4ad3f64edfe1c03e603316c1|$ROOT/sources/starloco-docker"; do
    IFS='|' read -r name url ref path <<<"$spec"
    printf '%s\t%s\t%s\t%s\n' "$name" "$url" "$ref" "$(git -C "$path" rev-parse HEAD)"
  done
} > "$ROOT/manifests/SOURCES.tsv"

printf 'Construction des images amont de référence…\n'
UPSTREAM="$ROOT/sources/starloco-docker"

# Les secrets Compose sont requis à la lecture du fichier amont, mais ils ne
# servent pas à construire les images. Des valeurs factices vivent donc dans
# un dossier temporaire hors des sources, puis sont supprimées même sur erreur.
rm -f -- \
  "$UPSTREAM/secrets/starloco_db_password.secret" \
  "$UPSTREAM/secrets/exchange_key.secret"
rmdir -- "$UPSTREAM/secrets" 2>/dev/null || true
PREP_SECRET_DIR="$(mktemp -d "$ROOT/runtime/prepare-build-secrets.XXXXXX")"
PREP_SECRET_OVERRIDE="$PREP_SECRET_DIR/compose-secrets.yml"
cleanup_prepare_build_secrets() {
  rm -f -- \
    "$PREP_SECRET_DIR/starloco_db_password.secret" \
    "$PREP_SECRET_DIR/exchange_key.secret" \
    "$PREP_SECRET_OVERRIDE"
  rmdir -- "$PREP_SECRET_DIR" 2>/dev/null || true
}
trap cleanup_prepare_build_secrets EXIT
printf 'build-only\n' > "$PREP_SECRET_DIR/starloco_db_password.secret"
printf 'build-only\n' > "$PREP_SECRET_DIR/exchange_key.secret"
chmod 600 \
  "$PREP_SECRET_DIR/starloco_db_password.secret" \
  "$PREP_SECRET_DIR/exchange_key.secret"
{
  printf 'secrets:\n'
  printf '  starloco_db_password_secret:\n'
  printf '    file: %s\n' "$PREP_SECRET_DIR/starloco_db_password.secret"
  printf '  exchange_key_secret:\n'
  printf '    file: %s\n' "$PREP_SECRET_DIR/exchange_key.secret"
} > "$PREP_SECRET_OVERRIDE"
(
  cd "$UPSTREAM"
  docker compose \
    -f docker-compose.yml \
    -f "$PREP_SECRET_OVERRIDE" \
    -p starloco-prep \
    build login game
)
cleanup_prepare_build_secrets
trap - EXIT

tag_built_service() {
  local service="$1" target="$2" image_ref="" image_id=""

  # Docker Compose v2 peut construire et nommer correctement une image tout en
  # ne renvoyant rien avec `compose images -q <service>` (notamment avec Bake).
  # On privilégie donc le nom de projet déterministe, puis on garde plusieurs
  # méthodes de repli compatibles avec les versions plus anciennes.
  for candidate in \
    "starloco-prep-${service}:latest" \
    "starloco-prep_${service}:latest"; do
    if docker image inspect "$candidate" >/dev/null 2>&1; then
      image_ref="$candidate"
      break
    fi
  done

  if [[ -z "$image_ref" ]]; then
    image_id="$(cd "$UPSTREAM" && docker compose -p starloco-prep images -q "$service" 2>/dev/null | head -n 1)"
    if [[ -n "$image_id" ]] && docker image inspect "$image_id" >/dev/null 2>&1; then
      image_ref="$image_id"
    fi
  fi

  if [[ -z "$image_ref" ]]; then
    image_ref="$(docker image ls \
      --filter "label=com.docker.compose.project=starloco-prep" \
      --filter "label=com.docker.compose.service=$service" \
      --format '{{.Repository}}:{{.Tag}}' | head -n 1)"
  fi

  [[ -n "$image_ref" ]] || die "Image construite introuvable pour $service. Images starloco-prep présentes : $(docker image ls --format '{{.Repository}}:{{.Tag}}' | grep '^starloco-prep[-_]' | tr '\n' ' ' || true)"
  docker tag "$image_ref" "$target"
  printf 'Image %-5s détectée : %s -> %s\n' "$service" "$image_ref" "$target"
}
tag_built_service login starloco-offline/login:prepared
tag_built_service game starloco-offline/game:prepared

printf 'Construction du portail corrigé…\n'
docker build -t starloco-offline/web:prepared "$STACK/web"

GRADLE_IMAGE="$(<"$ROOT/manifests/GRADLE_IMAGE")"
docker pull mariadb:11.3
docker pull redis:7-alpine
docker pull "$GRADLE_IMAGE"

warm_gradle() {
  local repo="$1"
  docker run --rm \
    -e GRADLE_USER_HOME=/gradle-cache \
    -v "$repo:/src" \
    -v "$ROOT/cache/gradle:/gradle-cache" \
    -w /src "$GRADLE_IMAGE" \
    gradle --no-daemon --stacktrace clean check jar
}
warm_gradle "$ROOT/serveur-jeu"
warm_gradle "$ROOT/sources/StarLoco-Login"

"$ROOT/offline-rebuild.sh" all --run-tests --no-restart

printf 'Création de l’archive d’images hors ligne…\n'
docker save \
  mariadb:11.3 redis:7-alpine "$GRADLE_IMAGE" \
  starloco-offline/login:prepared starloco-offline/game:prepared \
  starloco-offline/web:prepared \
  starloco-offline/login:custom starloco-offline/game:custom \
  -o "$IMAGE_BUNDLE"
(cd "$ROOT" && sha256sum "docker-images/$(basename "$IMAGE_BUNDLE")" > "docker-images/$(basename "$IMAGE_BUNDLE_CHECKSUM")")

printf 'Préparation connectée terminée.\n'
"$ROOT/generate-checksums.sh"
"$ROOT/offline-doctor.sh" --strict
