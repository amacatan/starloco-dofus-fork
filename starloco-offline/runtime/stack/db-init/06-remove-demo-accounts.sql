USE `starloco_login`;

-- Nettoyage réservé à l'initialisation d'un volume MariaDB neuf. Ce fichier
-- n'est volontairement pas rejoué par offline-migrate.sh sur une installation
-- existante : un ancien compte de démonstration a pu devenir un vrai personnage.
-- Un compte modifié ou recréé avec un autre mot de passe reste intact.
DELETE `player`
FROM `world_players` AS `player`
INNER JOIN `world_accounts` AS `account` ON `account`.`guid` = `player`.`account`
WHERE `account`.`account` IN ('test', 'test2', 'test3')
  AND `account`.`pass` = 'ff594f8cf10ca2e3ad4279375f0d0e688a7eca861000e7ecc63ae4b105c8be7bcb57e8c1172ea460c462c6f715508dc356fd964cf41644682db1feffd466769a';

DELETE FROM `world_accounts`
WHERE `account` IN ('test', 'test2', 'test3')
  AND `pass` = 'ff594f8cf10ca2e3ad4279375f0d0e688a7eca861000e7ecc63ae4b105c8be7bcb57e8c1172ea460c462c6f715508dc356fd964cf41644682db1feffd466769a';

-- Les premières versions du paquet supprimaient le compte 1 sans son personnage
-- de démonstration. Cette signature exacte répare uniquement cet orphelin connu.
DELETE FROM `world_players`
WHERE `id` = 1
  AND `account` = 1
  AND `name` = 'Bredravorveidurroth'
  AND NOT EXISTS (
    SELECT 1 FROM `world_accounts` WHERE `guid` = 1
  );
