USE `starloco_login`;

-- Les mutations du back-office doivent être atomiques avec leur journal.
-- Le serveur manipule ces tables par SQL standard et fonctionne avec InnoDB.
SET @world_players_needs_innodb = (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'world_players'
    AND ENGINE <> 'InnoDB'
);
SET @world_players_engine_sql = IF(
  @world_players_needs_innodb > 0,
  'ALTER TABLE `world_players` ENGINE = InnoDB',
  'SELECT ''Moteur world_players déjà prêt'' AS result'
);
PREPARE world_players_engine_stmt FROM @world_players_engine_sql;
EXECUTE world_players_engine_stmt;
DEALLOCATE PREPARE world_players_engine_stmt;

SET @world_objects_needs_innodb = (
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'world_objects'
    AND ENGINE <> 'InnoDB'
);
SET @world_objects_engine_sql = IF(
  @world_objects_needs_innodb > 0,
  'ALTER TABLE `world_objects` ENGINE = InnoDB',
  'SELECT ''Moteur world_objects déjà prêt'' AS result'
);
PREPARE world_objects_engine_stmt FROM @world_objects_engine_sql;
EXECUTE world_objects_engine_stmt;
DEALLOCATE PREPARE world_objects_engine_stmt;

SET @world_objects_has_primary_key = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'world_objects'
    AND INDEX_NAME = 'PRIMARY'
);
SET @world_objects_primary_sql = IF(
  @world_objects_has_primary_key = 0,
  'ALTER TABLE `world_objects` ADD PRIMARY KEY (`id`)',
  'SELECT ''Clé primaire world_objects déjà présente'' AS result'
);
PREPARE world_objects_primary_stmt FROM @world_objects_primary_sql;
EXECUTE world_objects_primary_stmt;
DEALLOCATE PREPARE world_objects_primary_stmt;

-- Journal append-only des mutations réalisées depuis le back-office.
-- Les mots de passe, e-mails et adresses IP en clair n'y sont jamais stockés.
CREATE TABLE IF NOT EXISTS `website_admin_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `request_id` char(32) NOT NULL,
  `actor` varchar(64) NOT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'owner',
  `action` varchar(48) NOT NULL,
  `outcome` varchar(16) NOT NULL DEFAULT 'success',
  `player_id` int NULL,
  `player_name` varchar(30) NULL,
  `summary` varchar(190) NOT NULL,
  `payload_json` longtext NULL,
  `source_hash` char(64) NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admin_audit_request` (`request_id`),
  KEY `idx_admin_audit_created` (`created_at`, `id`),
  KEY `idx_admin_audit_player` (`player_id`, `created_at`),
  KEY `idx_admin_audit_action` (`action`, `created_at`),
  CONSTRAINT `chk_admin_audit_payload_json`
    CHECK (`payload_json` IS NULL OR JSON_VALID(`payload_json`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Limitation persistante des tentatives de connexion, par empreinte réseau.
CREATE TABLE IF NOT EXISTS `website_admin_login_attempts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `source_hash` char(64) NOT NULL,
  `succeeded` tinyint(1) NOT NULL DEFAULT 0,
  `attempted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_attempt_source_date` (`source_hash`, `attempted_at`),
  KEY `idx_admin_attempt_date` (`attempted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
