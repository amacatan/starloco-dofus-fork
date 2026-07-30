#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

STRICT=false
DATABASE=false
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    --database) DATABASE=true ;;
    *) die "Usage : $0 [--strict] [--database]" ;;
  esac
done

errors=0
warnings=0
ok() { printf '[OK] %s\n' "$*"; }
warn() { printf '[AVERTISSEMENT] %s\n' "$*" >&2; warnings=$((warnings+1)); }
fail() { printf '[ÉCHEC] %s\n' "$*" >&2; errors=$((errors+1)); }

for command in bash cmp find realpath sha256sum tar; do
  command -v "$command" >/dev/null 2>&1 && ok "$command disponible" || fail "$command absent"
done

DOCKER_COMPOSE=false
DOCKER_DAEMON=false
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE=true
  if docker info >/dev/null 2>&1; then
    DOCKER_DAEMON=true
  fi
fi

for script in "$ROOT"/offline-*.sh "$ROOT"/game-dev.sh "$ROOT"/prepare-online.sh "$ROOT"/generate-checksums.sh; do
  bash -n "$script" && ok "syntaxe $(basename "$script")" || fail "syntaxe $(basename "$script")"
done

run_php_checks_on_host() {
  local file
  while IFS= read -r -d '' file; do
    php -l "$file" >/dev/null && ok "PHP $(realpath --relative-to="$ROOT" "$file")" ||
      fail "PHP invalide : $file"
  done < <(find "$STACK/web/public" "$STACK/tests" -type f -name '*.php' -print0)

  if php "$STACK/tests/run.php"; then
    ok "tests du portail public, de l’administration et de la création de compte (PHP hôte)"
  else
    fail "tests du portail public, de l’administration et de la création de compte"
  fi
}

run_php_checks_in_image() {
  local output
  if output="$(
    docker run --rm --network none --read-only --cap-drop ALL \
      --security-opt no-new-privileges:true \
      --tmpfs /tmp:size=16m,mode=1777 \
      -v "$STACK/tests:/audit/tests:ro" \
      -v "$STACK/web:/audit/web:ro" \
      -v "$STACK/db-init:/audit/db-init:ro" \
      -v "$STACK/docker-compose.yml:/audit/docker-compose.yml:ro" \
      -v "$ROOT/offline-migrate.sh:/audit/offline-migrate.sh:ro" \
      -v "$ROOT/serveur-jeu:/serveur-jeu:ro" \
      -v "$ROOT/sources/StarLoco-Login:/sources/StarLoco-Login:ro" \
      --entrypoint sh starloco-offline/web:prepared -ec \
      'find /audit/web/public /audit/tests -type f -name "*.php" -exec php -l "{}" ";" >/dev/null
       php /audit/tests/run.php'
  )"; then
    [[ -n "$output" ]] && printf '%s\n' "$output"
    ok "syntaxe et tests PHP dans l’image web, sans PHP hôte"
  else
    [[ -n "$output" ]] && printf '%s\n' "$output" >&2
    fail "syntaxe ou tests PHP dans l’image web"
  fi
}

if command -v php >/dev/null 2>&1; then
  ok "php disponible sur l’hôte"
  run_php_checks_on_host
elif $DOCKER_DAEMON && image_exists starloco-offline/web:prepared; then
  ok "PHP hôte absent, utilisation de l’image web"
  run_php_checks_in_image
else
  warn "PHP hôte absent et image web non chargée : tests PHP différés (lancez ./offline-load-images.sh)"
fi

required_files=(
  "$STACK/docker-compose.yml"
  "$STACK/db-init/02-login.sql"
  "$STACK/db-init/05-account-hardening.sql"
  "$STACK/db-init/06-disable-public-demo-accounts.sql"
  "$STACK/db-init/07-account-schema-assertions.sql"
  "$STACK/db-init/08-social-feed.sql"
  "$STACK/db-init/09-admin-console.sql"
  "$STACK/db-init/10-guild-features.sql"
  "$STACK/web/public/app/social.php"
  "$STACK/web/public/app/admin.php"
  "$STACK/web/public/app/admin-layout.php"
  "$STACK/web/public/app/players.php"
  "$STACK/web/public/admin/index.php"
  "$STACK/web/public/admin/player.php"
  "$STACK/web/public/admin/action.php"
  "$STACK/web/public/admin/login.php"
  "$STACK/web/public/admin/logout.php"
  "$STACK/web/public/admin/api/items.php"
  "$STACK/web/public/players.php"
  "$STACK/web/public/player.php"
  "$STACK/web/public/register.php"
  "$STACK/web/public/assets/admin.css"
  "$STACK/web/public/assets/admin.js"
  "$STACK/web/public/assets/players.css"
  "$STACK/web/Dockerfile"
  "$STACK/web/Dockerfile.offline"
  "$ROOT/offline-admin-password.sh"
  "$ROOT/game-dev.sh"
  "$ROOT/DEVELOPPEMENT-SERVEUR.md"
)
for sql_file in "${GAME_DB_INIT_FILES[@]}"; do
  required_files+=("$STACK/db-init/$sql_file")
done
for file in "${required_files[@]}"; do
  [[ -s "$file" ]] && ok "présent : $(realpath --relative-to="$ROOT" "$file")" ||
    fail "absent ou vide : $file"
done
[[ -x "$ROOT/offline-admin-password.sh" ]] &&
  ok "commande administrateur exécutable" ||
  fail "offline-admin-password.sh doit être exécutable"

if [[ -s "$STACK/db-init/04-game.sql" ]] &&
   [[ $(stat -c '%s' "$STACK/db-init/04-game.sql") -gt 50000000 ]]; then
  ok "base du jeu complète ($(du -h "$STACK/db-init/04-game.sql" | cut -f1))"
else
  fail "base du jeu absente ou tronquée"
fi

game_repo="$(game_source_dir)"
[[ -f "$game_repo/build.gradle" ]] && ok "sources présentes : serveur-jeu" ||
  warn "sources éditables du jeu absentes : $ROOT/serveur-jeu (game-dev.sh init les récupère)"
if [[ -d "$game_repo/db-init" ]]; then
  for sql_file in "${GAME_DB_INIT_FILES[@]}"; do
    if [[ -s "$game_repo/db-init/$sql_file" ]] &&
       cmp -s "$game_repo/db-init/$sql_file" "$STACK/db-init/$sql_file"; then
      ok "SQL jeu fidèle à la source : $sql_file"
    else
      fail "SQL jeu absent ou différent de la source : $sql_file"
    fi
  done
  if [[ -s "$game_repo/db-init/10-guild-features.sql" ]] &&
     cmp -s "$game_repo/db-init/10-guild-features.sql" \
       "$STACK/db-init/10-guild-features.sql"; then
    ok "SQL des fonctions de guilde fidèle à la source"
  else
    fail "migration des fonctions de guilde absente ou différente de la source"
  fi
fi
if grep -Eiq '^[[:space:]]*create[[:space:]]+table[[:space:]]+quest_progress' \
     "$STACK/db-init/06-update_game_23.04.23.sql" &&
   grep -Eiq '^[[:space:]]*create[[:space:]]+table[[:space:]]+quest_progress' \
     "$STACK/db-init/08-update_game_08.05.23.sql"; then
  ok "chaîne de correctifs jeu complète (table quest_progress)"
else
  fail "correctifs jeu incomplets : quest_progress ne sera pas initialisée"
fi
for path in "$ROOT/sources/StarLoco-Login"; do
  [[ -f "$path/build.gradle" ]] && ok "sources présentes : $(basename "$path")" ||
    warn "sources éditables absentes : $path (prepare-online.sh les récupère)"
done

if "$STACK/tests/dev-workflow.sh"; then
  ok "workflow de développement du serveur"
else
  fail "workflow de développement du serveur"
fi

if [[ -s "$IMAGE_BUNDLE" ]]; then
  if verify_image_bundle_checksum; then
    ok "archive d’images Docker intègre"
  else
    fail "archive d’images Docker corrompue ou somme de contrôle absente"
  fi

  bundle_manifest="$(tar -xOf "$IMAGE_BUNDLE" manifest.json 2>/dev/null || true)"
  bundle_images=(
    mariadb:11.3
    redis:7-alpine
    gradle:8.10.2-jdk21
    starloco-offline/login:prepared
    starloco-offline/login:custom
    starloco-offline/game:prepared
    starloco-offline/game:custom
    starloco-offline/web:prepared
  )
  for image in "${bundle_images[@]}"; do
    if grep -Fq "\"$image\"" <<<"$bundle_manifest"; then
      ok "image présente dans l’archive : $image"
    else
      fail "image absente de l’archive : $image"
    fi
  done
  if grep -Fq '"starloco-offline/zaap:' <<<"$bundle_manifest"; then
    fail "ancienne image HTTP Zaap encore présente dans l’archive"
  else
    ok "aucune ancienne image HTTP Zaap dans l’archive"
  fi
else
  warn "archive d’images Docker absente"
fi

if $DOCKER_COMPOSE; then
  compose_env="$STACK/.env"
  [[ -f "$compose_env" ]] || compose_env="$STACK/.env.example"
  if (cd "$STACK" && docker compose -f docker-compose.yml -f "$OVERRIDE" --env-file "$compose_env" config >/dev/null); then
    ok "configuration Docker Compose valide"
    mapfile -t configured_services < <(
      cd "$STACK"
      docker compose -f docker-compose.yml -f "$OVERRIDE" \
        --env-file "$compose_env" config --services
    )
    expected_services=(mariadb redis login game web social-worker)
    services_are_exact=true
    if (( ${#configured_services[@]} != ${#expected_services[@]} )); then
      services_are_exact=false
    else
      for service in "${expected_services[@]}"; do
        if ! printf '%s\n' "${configured_services[@]}" | grep -qx "$service"; then
          services_are_exact=false
          break
        fi
      done
    fi
    if $services_are_exact; then
      ok "six services attendus, sans ancienne API HTTP Zaap"
    else
      fail "services Compose inattendus : ${configured_services[*]:-aucun}"
    fi
  else
    fail "configuration Docker Compose invalide"
  fi

  if $DATABASE; then
    if $DOCKER_DAEMON &&
       compose ps --services --status running | grep -qx mariadb; then
      password="$(<"$STACK/secrets/starloco_db_password.secret")"
      account_schema_state="$(
        compose exec -T -e MYSQL_PWD="$password" mariadb \
          mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
          --batch --skip-column-names starloco_login \
          -e "SELECT CONCAT(
                COALESCE((
                  SELECT ENGINE FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA='starloco_login' AND TABLE_NAME='world_accounts'
                ), 'absent'),
                ':',
                (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME='world_accounts'
                   AND COLUMN_NAME IN (
                     'guid','account','pass','email','question',
                     'reponse','pseudo','dateRegister','reload_needed','logged'
                   )),
                ':',
                (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME='world_accounts'
                   AND (
                     (COLUMN_NAME='account' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=30 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='pass' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=128 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='email' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=100 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='question' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=100 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='reponse' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=100 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='pseudo' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=30 AND IS_NULLABLE='NO')
                     OR (COLUMN_NAME='dateRegister' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=10)
                   )),
                ':',
                (SELECT COUNT(*) FROM (
                  SELECT LOWER(TRIM(email)) FROM world_accounts
                  GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1
                ) AS duplicate_emails),
                ':',
                (SELECT COUNT(*) FROM (
                  SELECT LOWER(TRIM(pseudo)) FROM world_accounts
                  GROUP BY LOWER(TRIM(pseudo)) HAVING COUNT(*) > 1
                ) AS duplicate_pseudos)
              );" 2>/dev/null || true
      )"
      if [[ "$account_schema_state" == "InnoDB:10:7:0:0" ]]; then
        ok "schéma des comptes conforme, sans doublon e-mail/pseudo"
      else
        fail "schéma des comptes incohérent (attendu InnoDB:10:7:0:0, obtenu ${account_schema_state:-vide})"
      fi

      admin_schema_state="$(
        compose exec -T -e MYSQL_PWD="$password" mariadb \
          mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
          --batch --skip-column-names starloco_login \
          -e "SELECT CONCAT(
                (SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME IN ('world_players','world_objects')
                   AND ENGINE='InnoDB'),
                ':',
                IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA='starloco_login'
                      AND TABLE_NAME='world_objects'
                      AND INDEX_NAME='PRIMARY') > 0, 1, 0),
                ':',
                (SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME IN ('website_admin_audit','website_admin_login_attempts')
                   AND ENGINE='InnoDB')
              );" 2>/dev/null || true
      )"
      if [[ "$admin_schema_state" == "2:1:2" ]]; then
        ok "stockage transactionnel et journal de la console admin prêts"
      else
        fail "schéma admin incohérent (attendu 2:1:2, obtenu ${admin_schema_state:-vide})"
      fi

      guild_schema_state="$(
        compose exec -T -e MYSQL_PWD="$password" mariadb \
          mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
          --batch --skip-column-names starloco_login \
          -e "SELECT CONCAT(
                (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME='world_guilds'
                   AND COLUMN_NAME IN (
                     'note','note_author','note_date','informations',
                     'informations_author','informations_date','rank_names'
                   )),
                ':',
                (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA='starloco_login'
                   AND TABLE_NAME='world_guilds'
                   AND (
                     (COLUMN_NAME='note' AND CHARACTER_MAXIMUM_LENGTH=256)
                     OR (COLUMN_NAME='note_author' AND CHARACTER_MAXIMUM_LENGTH=50)
                     OR (COLUMN_NAME='note_date' AND DATA_TYPE='bigint')
                     OR (COLUMN_NAME='informations' AND CHARACTER_MAXIMUM_LENGTH=2560)
                     OR (COLUMN_NAME='informations_author' AND CHARACTER_MAXIMUM_LENGTH=50)
                     OR (COLUMN_NAME='informations_date' AND DATA_TYPE='bigint')
                     OR (COLUMN_NAME='rank_names' AND CHARACTER_MAXIMUM_LENGTH=2048)
                   ))
              );" 2>/dev/null || true
      )"
      if [[ "$guild_schema_state" == "7:7" ]]; then
        ok "stockage des notes, informations et rangs de guilde prêt"
      else
        fail "schéma des fonctions de guilde incohérent (attendu 7:7, obtenu ${guild_schema_state:-vide})"
      fi

      game_patch_state="$(
        compose exec -T -e MYSQL_PWD="$password" mariadb \
          mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
          --batch --skip-column-names starloco_game \
          -e "SELECT CONCAT(
                (SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA='starloco_game' AND TABLE_NAME='quest_progress'),
                ':',
                COALESCE((SELECT conditions FROM challenge WHERE id=44), -1)
              );" 2>/dev/null || true
      )"
      if [[ "$game_patch_state" == "1:17" ]]; then
        ok "correctifs SQL jeu 05–09 appliqués"
      else
        fail "correctifs SQL jeu absents ou incomplets (état attendu quest_progress:challenge44 = 1:17, obtenu ${game_patch_state:-vide})"
      fi

      migration_table_exists="$(
        compose exec -T -e MYSQL_PWD="$password" mariadb \
          mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
          --batch --skip-column-names starloco_game \
          -e "SELECT COUNT(*) FROM information_schema.TABLES
              WHERE TABLE_SCHEMA='starloco_game'
                AND TABLE_NAME='starloco_schema_migrations';" 2>/dev/null || true
      )"
      if [[ "$migration_table_exists" == "1" ]]; then
        for migration in "${GAME_DB_INIT_FILES[@]:1}"; do
          expected_checksum="$(sha256sum "$STACK/db-init/$migration" | cut -d' ' -f1)"
          recorded_checksum="$(
            compose exec -T -e MYSQL_PWD="$password" mariadb \
              mariadb --protocol=tcp -h127.0.0.1 -ustarloco \
              --batch --skip-column-names starloco_game \
              -e "SELECT COALESCE((
                    SELECT checksum FROM starloco_schema_migrations
                    WHERE migration='$migration'
                  ), '');" 2>/dev/null || true
          )"
          if [[ "$recorded_checksum" == "$expected_checksum" ]]; then
            ok "migration jeu enregistrée : $migration"
          else
            fail "migration jeu absente ou checksum différent : $migration"
          fi
        done
      else
        fail "registre des migrations jeu absent ; relancez ./offline-start.sh"
      fi

      health_body="$(compose exec -T web curl -sS http://127.0.0.1/health.php?deep=1 2>/dev/null || true)"
      if grep -q '"status":"ok"' <<<"$health_body"; then
        ok "portail, base et fil communautaire accessibles"
      else
        fail "healthcheck profond du portail"
        [[ -n "$health_body" ]] && printf 'Réponse du portail : %s\n' "$health_body" >&2
        compose logs --tail=80 web 2>&1 \
          | grep -E 'Health check failed|Social feed error|Registration error|PHP Fatal|SQLSTATE|web-entrypoint' \
          | tail -n 20 >&2 || true
      fi
      if compose ps --services --status running | grep -qx social-worker &&
         compose exec -T social-worker php /src/public/cli-social-health.php; then
        ok "collecteur du fil communautaire actif"
      else
        fail "collecteur du fil communautaire inactif"
      fi
    else
      fail "diagnostic base demandé mais MariaDB n’est pas démarrée"
    fi
  elif ! $DOCKER_DAEMON; then
    warn "démon Docker indisponible : validation dynamique non exécutée"
  fi
else
  warn "Docker indisponible : validation dynamique non exécutée"
fi

printf '\nDiagnostic : %d erreur(s), %d avertissement(s).\n' "$errors" "$warnings"
(( errors == 0 )) || exit 1
if $STRICT && (( warnings > 0 )); then
  exit 2
fi
