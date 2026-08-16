USE starloco_game;

-- Repair only the known legacy EPO mappings.  The old value predicates make
-- this migration safe to replay and preserve intentional local overrides.
UPDATE `pets`
SET `Epo` = 10809
WHERE `TemplateID` = 10802 AND `Epo` = 10808;

UPDATE `pets`
SET `Epo` = 10885
WHERE `TemplateID` = 10865 AND `Epo` = 0;

UPDATE `pets`
SET `Epo` = 10886
WHERE `TemplateID` = 10866 AND `Epo` = 0;

UPDATE `item_template`
SET `conditions` = 'PO=7714'
WHERE `id` = 10750
  AND `type` = 116
  AND `conditions` = '';

INSERT INTO `objectsactions` (`template`, `type`, `args`)
SELECT 10763, '10', ''
FROM DUAL
WHERE EXISTS (
    SELECT 1
    FROM `item_template`
    WHERE `id` = 10763
      AND `type` = 116
      AND `conditions` = 'PO=7705'
)
AND NOT EXISTS (
    SELECT 1
    FROM `objectsactions`
    WHERE `template` = 10763
);
