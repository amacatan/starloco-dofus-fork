USE `starloco_login`;

-- Sur une base existante, ne jamais supprimer les personnages qui auraient
-- été joués depuis l'installation. Les trois comptes de démonstration ne sont
-- que verrouillés tant qu'ils conservent exactement le mot de passe public
-- livré dans le dump. L'administrateur peut ensuite réinitialiser leur mot de
-- passe et lever le bannissement explicitement s'il souhaite les récupérer.
UPDATE `world_accounts`
SET
  `banned` = 1,
  `bannedTime` = 0,
  `logged` = 0
WHERE `account` IN ('test', 'test2', 'test3')
  AND `pass` = 'ff594f8cf10ca2e3ad4279375f0d0e688a7eca861000e7ecc63ae4b105c8be7bcb57e8c1172ea460c462c6f715508dc356fd964cf41644682db1feffd466769a';
