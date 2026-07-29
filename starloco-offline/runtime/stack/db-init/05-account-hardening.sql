USE `starloco_login`;

-- Migration relançable : aligne world_accounts avec le portail et rend les
-- insertions atomiques. Elle ne supprime ni ne renomme aucun compte existant.

ALTER TABLE `world_accounts`
  ENGINE = InnoDB,
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Répare uniquement les valeurs absentes des anciennes installations.
UPDATE `world_accounts`
SET
  `account` = COALESCE(NULLIF(TRIM(`account`), ''), CONCAT('legacy_', `guid`)),
  `pass` = COALESCE(NULLIF(TRIM(`pass`), ''), REPEAT('0', 128)),
  `email` = COALESCE(NULLIF(TRIM(`email`), ''), CONCAT('legacy-', `guid`, '@invalid.local')),
  `pseudo` = COALESCE(NULLIF(TRIM(`pseudo`), ''), CONCAT('legacy_', `guid`)),
  `question` = COALESCE(NULLIF(TRIM(`question`), ''), 'Question non renseignée'),
  `reponse` = COALESCE(NULLIF(TRIM(`reponse`), ''), 'non renseignée');

-- Le portail historique écrivait parfois "dd/mm/yy HH:mm" dans un varchar(10).
-- On convertit les formats connus avant de resserrer la colonne.
UPDATE `world_accounts`
SET `dateRegister` = CASE
  WHEN `dateRegister` IS NULL OR TRIM(`dateRegister`) = '' THEN NULL
  WHEN TRIM(`dateRegister`) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    THEN LEFT(TRIM(`dateRegister`), 10)
  WHEN STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%y %H:%i') IS NOT NULL
    THEN DATE_FORMAT(STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%y %H:%i'), '%Y-%m-%d')
  WHEN STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%Y %H:%i') IS NOT NULL
    THEN DATE_FORMAT(STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%Y %H:%i'), '%Y-%m-%d')
  WHEN STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%Y') IS NOT NULL
    THEN DATE_FORMAT(STR_TO_DATE(TRIM(`dateRegister`), '%d/%m/%Y'), '%Y-%m-%d')
  ELSE NULL
END;

ALTER TABLE `world_accounts`
  MODIFY `account` varchar(30) NOT NULL,
  MODIFY `pass` char(128) NOT NULL,
  MODIFY `email` varchar(100) NOT NULL,
  MODIFY `question` varchar(100) NOT NULL,
  MODIFY `reponse` varchar(100) NOT NULL,
  MODIFY `pseudo` varchar(30) NOT NULL,
  MODIFY `dateRegister` varchar(10) NULL DEFAULT NULL;

-- Les index e-mail/pseudo sont uniques lorsque les données existantes le
-- permettent. En présence de doublons historiques, un index normal est ajouté
-- et le diagnostic explique comment les corriger. Les requêtes sont relançables.

SET @email_index_exists = (
  SELECT COUNT(DISTINCT `index_name`)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'world_accounts'
    AND column_name = 'email'
);
SET @email_duplicates = (
  SELECT COUNT(*) FROM (
    SELECT LOWER(TRIM(`email`))
    FROM `world_accounts`
    GROUP BY LOWER(TRIM(`email`))
    HAVING COUNT(*) > 1
  ) AS duplicated_emails
);
SET @email_sql = IF(
  @email_index_exists > 0,
  'SELECT ''Index e-mail déjà présent'' AS result',
  IF(
    @email_duplicates = 0,
    'ALTER TABLE `world_accounts` ADD UNIQUE INDEX `uq_world_accounts_email` (`email`)',
    'ALTER TABLE `world_accounts` ADD INDEX `idx_world_accounts_email` (`email`)'
  )
);
PREPARE email_stmt FROM @email_sql;
EXECUTE email_stmt;
DEALLOCATE PREPARE email_stmt;

SET @pseudo_index_exists = (
  SELECT COUNT(DISTINCT `index_name`)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'world_accounts'
    AND column_name = 'pseudo'
);
SET @pseudo_duplicates = (
  SELECT COUNT(*) FROM (
    SELECT LOWER(TRIM(`pseudo`))
    FROM `world_accounts`
    GROUP BY LOWER(TRIM(`pseudo`))
    HAVING COUNT(*) > 1
  ) AS duplicated_pseudos
);
SET @pseudo_sql = IF(
  @pseudo_index_exists > 0,
  'SELECT ''Index pseudo déjà présent'' AS result',
  IF(
    @pseudo_duplicates = 0,
    'ALTER TABLE `world_accounts` ADD UNIQUE INDEX `uq_world_accounts_pseudo` (`pseudo`)',
    'ALTER TABLE `world_accounts` ADD INDEX `idx_world_accounts_pseudo` (`pseudo`)'
  )
);
PREPARE pseudo_stmt FROM @pseudo_sql;
EXECUTE pseudo_stmt;
DEALLOCATE PREPARE pseudo_stmt;
