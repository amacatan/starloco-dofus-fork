USE `starloco_login`;

-- Fil communautaire public. Aucune donnée privée de world_accounts n'est copiée.
CREATE TABLE IF NOT EXISTS `website_social_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_key` varchar(191) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `player_id` int NULL,
  `player_name` varchar(30) NULL,
  `class_id` smallint NULL,
  `title` varchar(160) NOT NULL,
  `detail` varchar(255) NOT NULL DEFAULT '',
  `importance` tinyint unsigned NOT NULL DEFAULT 1,
  `item_template_id` int NULL,
  `item_name` varchar(100) NULL,
  `item_level` int NULL,
  `item_quantity` int NULL,
  `guild_name` varchar(50) NULL,
  `old_value` bigint NULL,
  `new_value` bigint NULL,
  `happened_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `visible` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_social_event_key` (`event_key`),
  KEY `idx_social_events_visible_date` (`visible`, `happened_at`, `id`),
  KEY `idx_social_events_type_date` (`event_type`, `happened_at`, `id`),
  KEY `idx_social_events_player` (`player_id`, `happened_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `website_social_player_snapshots` (
  `player_id` int NOT NULL,
  `player_name` varchar(30) NOT NULL,
  `class_id` smallint NOT NULL,
  `level` int NOT NULL,
  `xp` bigint NOT NULL DEFAULT 0,
  `alignment` int NOT NULL DEFAULT 0,
  `alignment_level` int NOT NULL DEFAULT 0,
  `honor` int NOT NULL DEFAULT 0,
  `guild_id` int NULL,
  `guild_name` varchar(50) NULL,
  `logged` tinyint NOT NULL DEFAULT 0,
  `total_kills` int NOT NULL DEFAULT 0,
  `death_count` int NOT NULL DEFAULT 0,
  `first_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`player_id`),
  KEY `idx_social_player_level` (`level`, `xp`),
  KEY `idx_social_player_online` (`logged`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `website_social_inventory_snapshots` (
  `player_id` int NOT NULL,
  `object_id` int NOT NULL,
  `template_id` int NOT NULL,
  `quantity` int NOT NULL DEFAULT 1,
  `first_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`player_id`, `object_id`),
  KEY `idx_social_inventory_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `website_social_quest_snapshots` (
  `player_id` int NOT NULL,
  `quest_id` int NOT NULL,
  `quest_name` varchar(220) NOT NULL DEFAULT 'Quête accomplie',
  `first_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`player_id`, `quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `website_social_state` (
  `id` tinyint unsigned NOT NULL,
  `initialized` tinyint(1) NOT NULL DEFAULT 0,
  `initialized_at` datetime NULL,
  `last_scan_at` datetime NULL,
  `heartbeat_at` datetime NULL,
  `last_error` varchar(255) NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `website_social_state` (`id`, `initialized`)
VALUES (1, 0)
ON DUPLICATE KEY UPDATE `id` = VALUES(`id`);

INSERT INTO `website_social_events`
  (`event_key`, `event_type`, `title`, `detail`, `importance`, `happened_at`)
VALUES
  ('system:community-feed-created', 'system', 'Le fil des aventuriers est ouvert',
   'Les exploits publics du serveur apparaîtront ici automatiquement.', 2, NOW())
ON DUPLICATE KEY UPDATE `event_key` = VALUES(`event_key`);
