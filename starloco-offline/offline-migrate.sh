#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

MODE="full"
case "${1:-}" in
  "") ;;
  --social-only) MODE="social" ;;
  --guild-features-only) MODE="guild-features" ;;
  -h|--help)
    cat <<'HELP'
Usage : ./offline-migrate.sh [--social-only|--guild-features-only]

Sans option, applique les migrations Login et Jeu. Les services applicatifs
doivent être arrêtés ; utilisez normalement ./offline-start.sh.

--social-only crée ou met à niveau uniquement les tables du fil communautaire.
--guild-features-only applique uniquement l’extension additive des guildes ;
elle peut précéder sans interruption le redéploiement du serveur de jeu.
HELP
    exit 0
    ;;
  *) die "Usage : $0 [--social-only|--guild-features-only]" ;;
esac

need_docker
ensure_runtime_files

running_services="$(compose ps --services --status running 2>/dev/null || true)"
grep -qx mariadb <<<"$running_services" ||
  die "MariaDB n’est pas démarrée. Lancez d’abord ./offline-start.sh."

if [[ "$MODE" == "full" ]]; then
  for service in login game web social-worker; do
    if grep -qx "$service" <<<"$running_services"; then
      die "Le service $service est encore actif. La migration complète doit passer par ./offline-start.sh."
    fi
  done
fi

wait_for_mariadb
password="$(<"$STACK/secrets/starloco_db_password.secret")"

login_sql() {
  compose exec -T -e MYSQL_PWD="$password" mariadb \
    mariadb --protocol=tcp -h127.0.0.1 -ustarloco starloco_login "$@"
}

game_sql() {
  compose exec -T -e MYSQL_PWD="$password" mariadb \
    mariadb --protocol=tcp -h127.0.0.1 -ustarloco starloco_game "$@"
}

game_scalar() {
  game_sql --batch --skip-column-names -e "$1" | tr -d '\r'
}

if [[ "$MODE" == "social" ]] && grep -qx game <<<"$running_services"; then
  admin_schema_ready="$(login_sql --batch --skip-column-names -e "
    SELECT IF(
      (SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME IN ('world_players', 'world_objects')
         AND ENGINE = 'InnoDB') = 2
      AND
      (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'world_objects'
         AND INDEX_NAME = 'PRIMARY') > 0,
      1, 0
    );
  " | tr -d '\r')"
  [[ "$admin_schema_ready" == "1" ]] ||
    die "Le schéma admin doit convertir des tables. Arrêtez Game ou lancez ./offline-start.sh sans --portal-only."
fi

if [[ "$MODE" == "social" ]]; then
  login_migrations=(
    "$STACK/db-init/08-social-feed.sql"
    "$STACK/db-init/09-admin-console.sql"
  )
elif [[ "$MODE" == "guild-features" ]]; then
  login_migrations=(
    "$STACK/db-init/10-guild-features.sql"
  )
else
  login_migrations=(
    "$STACK/db-init/03-login-zaap-index-update.sql"
    "$STACK/db-init/05-account-hardening.sql"
    "$STACK/db-init/06-disable-public-demo-accounts.sql"
    "$STACK/db-init/07-account-schema-assertions.sql"
    "$STACK/db-init/08-social-feed.sql"
    "$STACK/db-init/09-admin-console.sql"
    "$STACK/db-init/10-guild-features.sql"
  )
fi

for migration in "${login_migrations[@]}"; do
  printf 'Application de %s…\n' "$(basename "$migration")"
  login_sql < "$migration"
done

if [[ "$MODE" == "social" ]]; then
  printf 'Tables du fil communautaire prêtes.\n'
  exit 0
fi
if [[ "$MODE" == "guild-features" ]]; then
  printf 'Schéma des fonctionnalités de guilde prêt.\n'
  exit 0
fi

printf 'Contrôle du schéma des comptes…\n'
login_sql --batch --skip-column-names <<'SQL'
SELECT CONCAT(
  'engine=', ENGINE,
  ', collation=', TABLE_COLLATION
)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'world_accounts';

SELECT CONCAT('doublons_email=', COUNT(*))
FROM (
  SELECT LOWER(TRIM(email))
  FROM world_accounts
  GROUP BY LOWER(TRIM(email))
  HAVING COUNT(*) > 1
) AS duplicate_emails;

SELECT CONCAT('doublons_pseudo=', COUNT(*))
FROM (
  SELECT LOWER(TRIM(pseudo))
  FROM world_accounts
  GROUP BY LOWER(TRIM(pseudo))
  HAVING COUNT(*) > 1
) AS duplicate_pseudos;
SQL

game_sql <<'SQL'
CREATE TABLE IF NOT EXISTS `starloco_schema_migrations` (
  `migration` varchar(191) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`migration`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

quest_progress_schema_is_final() {
  [[ "$(game_scalar "
    SELECT IF(
      (SELECT COUNT(*)
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quest_progress') = 6
      AND
      (SELECT COUNT(*)
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quest_progress'
          AND COLUMN_NAME IN (
            'account_id', 'player_id', 'quest_id', 'current_step',
            'completed_objectives', 'finished'
          )) = 6
      AND
      (SELECT COUNT(*)
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quest_progress'
          AND INDEX_NAME = 'PRIMARY') = 3,
      1, 0
    );
  ")" == "1" ]]
}

game_migration_is_final() {
  local migration="$1" expected_crafts expected_quests expected_objectives
  local expected_steps expected_drops expected_donjons

  case "$migration" in
    05-update_game_16.04.23.sql)
      expected_crafts="$(grep -c '^INSERT INTO `crafts`' "$STACK/db-init/$migration")"
      [[ "$(game_scalar "
        SELECT IF(
          (SELECT COUNT(*) FROM crafts) = $expected_crafts
          AND EXISTS (
            SELECT 1 FROM crafts
             WHERE id = 12825
               AND craft = '1826*100;1953*100;2636*25;750*5;7663*5;12819*1;9941*1;10715*1'
          )
          AND NOT EXISTS (
            SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'experience'
          ),
          1, 0
        );
      ")" == "1" ]]
      ;;
    06-update_game_23.04.23.sql)
      quest_progress_schema_is_final
      ;;
    07-update_game_24.04.23.sql)
      expected_quests="$(grep -c '^INSERT INTO `quest`' "$STACK/db-init/$migration")"
      expected_objectives="$(grep -c '^INSERT INTO `quest_objective`' "$STACK/db-init/$migration")"
      expected_steps="$(grep -c '^INSERT INTO `quest_step`' "$STACK/db-init/$migration")"
      [[ "$(game_scalar "
        SELECT IF(
          (SELECT COUNT(*) FROM quest) = $expected_quests
          AND (SELECT COUNT(*) FROM quest_objective) = $expected_objectives
          AND (SELECT COUNT(*) FROM quest_step) = $expected_steps
          AND EXISTS (SELECT 1 FROM quest_step WHERE id = 1164),
          1, 0
        );
      ")" == "1" ]]
      ;;
    08-update_game_08.05.23.sql)
      expected_drops="$(grep -c '^INSERT INTO `drops`' "$STACK/db-init/$migration")"
      expected_donjons="$(grep -c '^INSERT INTO `donjons`' "$STACK/db-init/$migration")"
      quest_progress_schema_is_final &&
        [[ "$(game_scalar "
          SELECT IF(
            (SELECT COUNT(*) FROM drops) = $expected_drops
            AND (SELECT COUNT(*) FROM donjons) = $expected_donjons
            AND EXISTS (
              SELECT 1 FROM drops
               WHERE monsterId = 2821 AND objectId = 7036
            )
            AND EXISTS (
              SELECT 1 FROM donjons
               WHERE map = 8541 AND npc = 784 AND \`key\` = '8320'
            ),
            1, 0
          );
        ")" == "1" ]]
      ;;
    09-update_game_10.03.2024.sql)
      [[ "$(game_scalar "SELECT IF(COALESCE((SELECT conditions FROM challenge WHERE id = 44), -1) = 17, 1, 0);")" == "1" ]]
      ;;
    10-update_game_pet_epo.sql)
      [[ "$(game_scalar "
        SELECT IF(
          (
            NOT EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10802)
            OR (
              EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10802 AND Epo = 10809)
              AND EXISTS (SELECT 1 FROM item_template WHERE id = 10809 AND type = 116 AND conditions = 'PO=10802')
              AND EXISTS (SELECT 1 FROM objectsactions WHERE template = 10809 AND type = '10')
            )
          )
          AND (
            NOT EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10865)
            OR (
              EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10865 AND Epo = 10885)
              AND EXISTS (SELECT 1 FROM item_template WHERE id = 10885 AND type = 116 AND conditions = 'PO=10865')
              AND EXISTS (SELECT 1 FROM objectsactions WHERE template = 10885 AND type = '10')
            )
          )
          AND (
            NOT EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10866)
            OR (
              EXISTS (SELECT 1 FROM pets WHERE TemplateID = 10866 AND Epo = 10886)
              AND EXISTS (SELECT 1 FROM item_template WHERE id = 10886 AND type = 116 AND conditions = 'PO=10866')
              AND EXISTS (SELECT 1 FROM objectsactions WHERE template = 10886 AND type = '10')
            )
          )
          AND (
            NOT EXISTS (SELECT 1 FROM item_template WHERE id = 10750)
            OR (
              EXISTS (SELECT 1 FROM item_template WHERE id = 10750 AND type = 116 AND conditions = 'PO=7714')
              AND EXISTS (SELECT 1 FROM pets WHERE TemplateID = 7714 AND Epo = 10750)
              AND EXISTS (SELECT 1 FROM objectsactions WHERE template = 10750 AND type = '10')
            )
          )
          AND (
            NOT EXISTS (SELECT 1 FROM item_template WHERE id = 10763)
            OR (
              EXISTS (SELECT 1 FROM item_template WHERE id = 10763 AND type = 116 AND conditions = 'PO=7705')
              AND EXISTS (SELECT 1 FROM pets WHERE TemplateID = 7705 AND Epo = 10763)
              AND EXISTS (SELECT 1 FROM objectsactions WHERE template = 10763 AND type = '10')
            )
          ),
          1, 0
        );
      ")" == "1" ]]
      ;;
    *)
      die "Correctif jeu inconnu : $migration"
      ;;
  esac
}

backup_quest_progress() {
  quest_progress_schema_is_final ||
    die "Impossible de sauvegarder quest_progress : schéma source inattendu."
  game_sql <<'SQL'
CREATE TABLE IF NOT EXISTS `starloco_quest_progress_before_08`
  LIKE `quest_progress`;
REPLACE INTO `starloco_quest_progress_before_08` (
  `account_id`, `player_id`, `quest_id`, `current_step`,
  `completed_objectives`, `finished`
)
SELECT
  `account_id`, `player_id`, `quest_id`, `current_step`,
  `completed_objectives`, `finished`
FROM `quest_progress`;
SQL
}

restore_quest_progress() {
  local backup_exists
  backup_exists="$(game_scalar "
    SELECT COUNT(*)
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'starloco_quest_progress_before_08';
  ")"
  [[ "$backup_exists" == "1" ]] || return 0
  quest_progress_schema_is_final || return 1
  game_sql <<'SQL'
REPLACE INTO `quest_progress` (
  `account_id`, `player_id`, `quest_id`, `current_step`,
  `completed_objectives`, `finished`
)
SELECT
  `account_id`, `player_id`, `quest_id`, `current_step`,
  `completed_objectives`, `finished`
FROM `starloco_quest_progress_before_08`;
SQL
}

drop_quest_progress_backup() {
  game_sql -e 'DROP TABLE IF EXISTS `starloco_quest_progress_before_08`;'
}

record_game_migration() {
  local migration="$1" checksum="$2"
  game_sql -e "
    INSERT INTO starloco_schema_migrations (migration, checksum)
    VALUES ('$migration', '$checksum');
  "
}

apply_game_migrations() {
  local migration path checksum recorded_checksum

  for migration in "${GAME_DB_INIT_FILES[@]:1}"; do
    path="$STACK/db-init/$migration"
    [[ -s "$path" ]] || die "Correctif jeu absent ou vide : $path"
    checksum="$(sha256sum "$path" | cut -d' ' -f1)"
    recorded_checksum="$(game_scalar "
      SELECT COALESCE(
        (SELECT checksum
           FROM starloco_schema_migrations
          WHERE migration = '$migration'),
        ''
      );
    ")"

    if [[ -n "$recorded_checksum" ]]; then
      [[ "$recorded_checksum" == "$checksum" ]] ||
        die "Le checksum enregistré pour $migration ne correspond plus au fichier livré."
      if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
        restore_quest_progress ||
          die "La restauration différée de quest_progress a échoué."
        drop_quest_progress_backup
      fi
      printf 'Correctif jeu déjà appliqué : %s (checksum vérifié).\n' "$migration"
      continue
    fi

    if game_migration_is_final "$migration"; then
      if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
        restore_quest_progress ||
          die "La restauration différée de quest_progress a échoué."
      fi
      record_game_migration "$migration" "$checksum"
      if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
        drop_quest_progress_backup
      fi
      printf 'État final déjà présent : %s (marqueur créé sans rejeu).\n' "$migration"
      continue
    fi

    printf 'Application unique du correctif jeu %s…\n' "$migration"
    if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
      backup_quest_progress
    fi

    if ! game_sql < "$path"; then
      if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
        restore_quest_progress || true
      fi
      die "Échec du correctif jeu $migration ; aucun marqueur n’a été écrit."
    fi

    if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
      restore_quest_progress ||
        die "Correctif 08 appliqué, mais restauration de quest_progress impossible."
    fi
    game_migration_is_final "$migration" ||
      die "Le correctif $migration s’est terminé sans produire l’état final attendu."
    record_game_migration "$migration" "$checksum"
    if [[ "$migration" == "08-update_game_08.05.23.sql" ]]; then
      drop_quest_progress_backup
    fi
  done
}

apply_game_migrations

printf 'Migrations terminées, fil communautaire et données de jeu prêts.\n'
