USE `starloco_login`;

DELIMITER //
DROP PROCEDURE IF EXISTS `assert_world_accounts_schema`//
CREATE PROCEDURE `assert_world_accounts_schema`()
BEGIN
  DECLARE required_columns INT DEFAULT 0;
  DECLARE correct_columns INT DEFAULT 0;
  DECLARE table_engine VARCHAR(64) DEFAULT NULL;

  SELECT COUNT(*) INTO required_columns
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'world_accounts'
    AND COLUMN_NAME IN (
      'guid', 'account', 'pass', 'email', 'question',
      'reponse', 'pseudo', 'dateRegister', 'reload_needed', 'logged'
    );

  SELECT COUNT(*) INTO correct_columns
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'world_accounts'
    AND (
      (COLUMN_NAME = 'account' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 30 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'pass' AND DATA_TYPE = 'char' AND CHARACTER_MAXIMUM_LENGTH = 128 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'email' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 100 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'question' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 100 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'reponse' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 100 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'pseudo' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 30 AND IS_NULLABLE = 'NO')
      OR (COLUMN_NAME = 'dateRegister' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 10)
    );

  SELECT ENGINE INTO table_engine
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'world_accounts';

  IF required_columns <> 10 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'world_accounts: colonnes obligatoires manquantes';
  END IF;

  IF correct_columns <> 7 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'world_accounts: type ou longueur de colonne incohérent';
  END IF;

  IF table_engine <> 'InnoDB' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'world_accounts: moteur InnoDB requis';
  END IF;
END//
CALL `assert_world_accounts_schema`()//
DROP PROCEDURE `assert_world_accounts_schema`//
DELIMITER ;
