<?php
declare(strict_types=1);

require_once __DIR__ . '/social.php';

/**
 * Ce fichier est le seul accès aux données des pages joueurs publiques.
 * Il ne sélectionne jamais world_accounts, account, e-mail, kamas, IP,
 * position géographique ou inventaire non équipé.
 */

/** @return array{name:string,glyph:string} */
function players_class_meta(int $classId): array
{
    return social_class_meta($classId);
}

/** @return array{name:string,glyph:string,tone:string} */
function players_alignment_meta(int $alignment): array
{
    return match ($alignment) {
        1 => ['name' => 'Bontarien', 'glyph' => '◈', 'tone' => 'bonta'],
        2 => ['name' => 'Brâkmarien', 'glyph' => '◆', 'tone' => 'brakmar'],
        3 => ['name' => 'Mercenaire', 'glyph' => '✦', 'tone' => 'mercenary'],
        default => ['name' => 'Neutre', 'glyph' => '◇', 'tone' => 'neutral'],
    };
}

function players_guild_rank_name(int $rank): string
{
    return [
        0 => 'À l’essai',
        1 => 'Meneur',
        2 => 'Bras droit',
        3 => 'Trésorier',
        4 => 'Protecteur',
        5 => 'Artisan',
        6 => 'Réserviste',
        7 => 'Larbin',
        8 => 'Gardien',
        9 => 'Éclaireur',
        10 => 'Espion',
        11 => 'Diplomate',
        12 => 'Secrétaire',
        13 => 'Tueur de familiers',
        14 => 'Braconnier',
        15 => 'Chercheur de trésors',
        16 => 'Voleur',
        17 => 'Initié',
        18 => 'Assassin',
        19 => 'Gouverneur',
        20 => 'Muse',
        21 => 'Conseiller',
        22 => 'Élu',
        23 => 'Guide',
        24 => 'Mentor',
        25 => 'Recruteur',
        26 => 'Éleveur',
        27 => 'Marchand',
        28 => 'Apprenti',
        29 => 'Bourreau',
        30 => 'Mascotte',
        31 => 'Pénitent',
        32 => 'Déserteur',
        33 => 'Traître',
        34 => 'Boulet',
        35 => 'Larbin',
        36 => 'Rival',
        37 => 'Recrue',
    ][$rank] ?? 'Membre';
}

/** @return array{label:string,glyph:string} */
function players_equipment_slot_meta(int $position): array
{
    return [
        0 => ['label' => 'Amulette', 'glyph' => '◇'],
        1 => ['label' => 'Arme', 'glyph' => '†'],
        2 => ['label' => 'Anneau gauche', 'glyph' => '○'],
        3 => ['label' => 'Ceinture', 'glyph' => '═'],
        4 => ['label' => 'Anneau droit', 'glyph' => '○'],
        5 => ['label' => 'Bottes', 'glyph' => '⌁'],
        6 => ['label' => 'Coiffe', 'glyph' => '△'],
        7 => ['label' => 'Cape', 'glyph' => '◩'],
        8 => ['label' => 'Familier', 'glyph' => '♢'],
        9 => ['label' => 'Dofus I', 'glyph' => '●'],
        10 => ['label' => 'Dofus II', 'glyph' => '●'],
        11 => ['label' => 'Dofus III', 'glyph' => '●'],
        12 => ['label' => 'Dofus IV', 'glyph' => '●'],
        13 => ['label' => 'Dofus V', 'glyph' => '●'],
        14 => ['label' => 'Dofus VI', 'glyph' => '●'],
        15 => ['label' => 'Bouclier', 'glyph' => '⬡'],
        16 => ['label' => 'Monture', 'glyph' => '♞'],
    ][$position] ?? ['label' => 'Équipement', 'glyph' => '◆'];
}

function players_public_number(int|float $value): string
{
    return number_format($value, 0, ',', ' ');
}

function players_text_excerpt(string $value, int $maximum): string
{
    $value = trim((string) preg_replace('/[\x00-\x1F\x7F]/', '', $value));
    if (function_exists('mb_substr')) {
        return mb_substr($value, 0, $maximum, 'UTF-8');
    }
    return substr($value, 0, $maximum);
}

/**
 * @param array<string,mixed> $source
 * @return array{q:string,class:string,status:string,sort:string}
 */
function players_normalize_filters(array $source): array
{
    $scalar = static fn(string $key, string $fallback): string =>
        is_scalar($source[$key] ?? null) ? (string) $source[$key] : $fallback;

    $query = players_text_excerpt($scalar('q', ''), 30);
    $class = $scalar('class', 'all');
    $status = $scalar('status', 'all');
    $sort = $scalar('sort', 'level');

    if ($class !== 'all' && (!ctype_digit($class) || (int) $class < 1 || (int) $class > 12)) {
        $class = 'all';
    }
    if (!in_array($status, ['all', 'online', 'offline'], true)) {
        $status = 'all';
    }
    if (!in_array($sort, ['level', 'name', 'pvp', 'combat', 'recent'], true)) {
        $sort = 'level';
    }

    return ['q' => $query, 'class' => $class, 'status' => $status, 'sort' => $sort];
}

/**
 * @param array{q:string,class:string,status:string,sort:string} $filters
 * @return array{sql:list<string>,params:array<string,int|string>}
 */
function players_directory_conditions(array $filters): array
{
    $conditions = ['p.groupe = 0'];
    $params = [];

    if ($filters['q'] !== '') {
        $conditions[] = "p.name LIKE :player_query ESCAPE '!'";
        $params['player_query'] = '%' . strtr($filters['q'], [
            '!' => '!!',
            '%' => '!%',
            '_' => '!_',
        ]) . '%';
    }
    if ($filters['class'] !== 'all') {
        $conditions[] = 'p.class = :class_id';
        $params['class_id'] = (int) $filters['class'];
    }
    if ($filters['status'] === 'online') {
        $conditions[] = 'p.logged = 1';
    } elseif ($filters['status'] === 'offline') {
        $conditions[] = '(p.logged IS NULL OR p.logged <> 1)';
    }

    return ['sql' => $conditions, 'params' => $params];
}

/**
 * @param PDOStatement $statement
 * @param array<string,int|string> $params
 */
function players_bind_public_params(PDOStatement $statement, array $params): void
{
    foreach ($params as $name => $value) {
        $statement->bindValue(':' . $name, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
    }
}

/**
 * @param array{q:string,class:string,status:string,sort:string} $filters
 * @return array{players:list<array<string,mixed>>,total:int}
 */
function players_get_directory(PDO $pdo, array $filters, int $limit = 60): array
{
    $limit = max(1, min(100, $limit));
    $where = players_directory_conditions($filters);
    $whereSql = implode(' AND ', $where['sql']);

    $countStatement = $pdo->prepare('SELECT COUNT(*) FROM world_players p WHERE ' . $whereSql);
    players_bind_public_params($countStatement, $where['params']);
    $countStatement->execute();
    $total = (int) $countStatement->fetchColumn();

    $orderBy = match ($filters['sort']) {
        'name' => 'p.name ASC, p.id ASC',
        'pvp' => 'p.honor DESC, p.alvl DESC, p.level DESC, p.xp DESC, p.id ASC',
        'combat' => 'p.totalKills DESC, p.deathCount ASC, p.level DESC, p.id ASC',
        'recent' => 'p.id DESC',
        default => 'p.level DESC, p.xp DESC, p.id ASC',
    };

    $statement = $pdo->prepare(
        'SELECT p.id, p.name, p.class AS class_id, p.level, p.xp, '
        . 'p.alignement AS alignment, p.alvl AS alignment_level, p.honor, p.logged, '
        . 'p.totalKills AS total_kills, p.deathCount AS death_count, '
        . 'gm.guild AS guild_id, gm.rank AS guild_rank, g.name AS guild_name '
        . 'FROM world_players p '
        . 'LEFT JOIN starloco_game.guild_members gm ON gm.guid = p.id '
        . 'LEFT JOIN world_guilds g ON g.id = gm.guild '
        . 'WHERE ' . $whereSql . ' ORDER BY ' . $orderBy . ' LIMIT :result_limit'
    );
    players_bind_public_params($statement, $where['params']);
    $statement->bindValue(':result_limit', $limit, PDO::PARAM_INT);
    $statement->execute();

    $players = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $class = players_class_meta((int) $row['class_id']);
        $alignment = players_alignment_meta((int) $row['alignment']);
        $players[] = [
            'id' => (int) $row['id'],
            'name' => (string) $row['name'],
            'classId' => (int) $row['class_id'],
            'className' => $class['name'],
            'classGlyph' => $class['glyph'],
            'level' => max(1, (int) $row['level']),
            'xp' => max(0, (int) $row['xp']),
            'alignment' => $alignment,
            'alignmentLevel' => max(0, (int) $row['alignment_level']),
            'honor' => max(0, (int) $row['honor']),
            'online' => (int) $row['logged'] === 1,
            'totalKills' => max(0, (int) $row['total_kills']),
            'deathCount' => max(0, (int) $row['death_count']),
            'guildId' => $row['guild_id'] === null ? null : (int) $row['guild_id'],
            'guildName' => $row['guild_name'] === null ? null : (string) $row['guild_name'],
            'guildRank' => $row['guild_rank'] === null ? null : players_guild_rank_name((int) $row['guild_rank']),
        ];
    }

    return ['players' => $players, 'total' => $total];
}

/** @return array{characters:int,online:int,maxLevel:int,level200:int,guilds:int} */
function players_get_public_stats(PDO $pdo): array
{
    $row = $pdo->query(
        'SELECT COUNT(*) AS characters, COALESCE(SUM(logged = 1), 0) AS online, '
        . 'COALESCE(MAX(level), 0) AS max_level, COALESCE(SUM(level >= 200), 0) AS level_200 '
        . 'FROM world_players WHERE groupe = 0'
    )->fetch(PDO::FETCH_ASSOC);
    $guilds = (int) $pdo->query('SELECT COUNT(*) FROM world_guilds')->fetchColumn();

    return [
        'characters' => (int) ($row['characters'] ?? 0),
        'online' => (int) ($row['online'] ?? 0),
        'maxLevel' => (int) ($row['max_level'] ?? 0),
        'level200' => (int) ($row['level_200'] ?? 0),
        'guilds' => $guilds,
    ];
}

/**
 * @return array<int,array{position:int,label:string,glyph:string,item:?array<string,mixed>}>
 */
function players_get_equipment(PDO $pdo, string $rawObjectIds): array
{
    $equipment = [];
    for ($position = 0; $position <= 16; $position++) {
        $slot = players_equipment_slot_meta($position);
        $equipment[$position] = [
            'position' => $position,
            'label' => $slot['label'],
            'glyph' => $slot['glyph'],
            'item' => null,
        ];
    }

    $objectIds = array_slice(social_parse_object_ids($rawObjectIds), 0, 500);
    if ($objectIds === []) {
        return $equipment;
    }

    foreach (array_chunk($objectIds, 100) as $chunk) {
        $placeholders = implode(',', array_fill(0, count($chunk), '?'));
        $statement = $pdo->prepare(
            'SELECT o.template AS template_id, o.quantity, o.position, '
            . 't.name, t.level, t.type '
            . 'FROM world_objects o '
            . 'LEFT JOIN starloco_game.item_template t ON t.id = o.template '
            . "WHERE o.id IN ($placeholders) AND o.position BETWEEN 0 AND 16"
        );
        foreach (array_values($chunk) as $index => $objectId) {
            $statement->bindValue($index + 1, $objectId, PDO::PARAM_INT);
        }
        $statement->execute();

        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $position = (int) $row['position'];
            if (!array_key_exists($position, $equipment)) {
                continue;
            }
            $equipment[$position]['item'] = [
                'templateId' => (int) $row['template_id'],
                'name' => $row['name'] === null || (string) $row['name'] === ''
                    ? 'Objet #' . (int) $row['template_id']
                    : (string) $row['name'],
                'level' => max(1, (int) ($row['level'] ?? 1)),
                'quantity' => max(1, (int) $row['quantity']),
                'type' => (int) ($row['type'] ?? -1),
            ];
        }
    }

    return $equipment;
}

/**
 * @return array{available:bool,completed:int,recent:list<array{id:int,name:string,completedAt:?string}>}
 */
function players_get_quest_progress(PDO $pdo, int $playerId): array
{
    $result = ['available' => false, 'completed' => 0, 'recent' => []];

    try {
        $count = $pdo->prepare(
            'SELECT COUNT(*) FROM ('
            . 'SELECT qp.quest_id FROM starloco_game.quest_progress qp '
            . 'WHERE qp.player_id = :current_player AND qp.finished = 1 '
            . 'UNION '
            . 'SELECT legacy.quest FROM world_players_quests legacy '
            . 'WHERE legacy.player = :legacy_player AND legacy.finish = 1'
            . ') completed'
        );
        $count->bindValue(':current_player', $playerId, PDO::PARAM_INT);
        $count->bindValue(':legacy_player', $playerId, PDO::PARAM_INT);
        $count->execute();
        $result['available'] = true;
        $result['completed'] = (int) $count->fetchColumn();
    } catch (Throwable $exception) {
        error_log('Public player quest count error: ' . $exception->getMessage());
        return $result;
    }

    try {
        $recent = $pdo->prepare(
            'SELECT quest_id, quest_name, first_seen_at '
            . 'FROM website_social_quest_snapshots '
            . 'WHERE player_id = :player_id ORDER BY first_seen_at DESC, quest_id DESC LIMIT 6'
        );
        $recent->bindValue(':player_id', $playerId, PDO::PARAM_INT);
        $recent->execute();
        foreach ($recent->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $result['recent'][] = [
                'id' => (int) $row['quest_id'],
                'name' => (string) $row['quest_name'],
                'completedAt' => $row['first_seen_at'] === null ? null : (string) $row['first_seen_at'],
            ];
        }
    } catch (Throwable $exception) {
        error_log('Public player recent quests error: ' . $exception->getMessage());
    }

    return $result;
}

/** @return list<array<string,mixed>> */
function players_get_recent_activity(PDO $pdo, int $playerId): array
{
    try {
        $statement = $pdo->prepare(
            'SELECT id, event_type, title, detail, importance, item_name, guild_name, happened_at '
            . 'FROM website_social_events '
            . 'WHERE visible = 1 AND player_id = :player_id '
            . 'ORDER BY happened_at DESC, id DESC LIMIT 8'
        );
        $statement->bindValue(':player_id', $playerId, PDO::PARAM_INT);
        $statement->execute();

        $events = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $meta = social_event_meta((string) $row['event_type']);
            $events[] = [
                'id' => (int) $row['id'],
                'type' => (string) $row['event_type'],
                'label' => $meta['label'],
                'icon' => $meta['icon'],
                'title' => (string) $row['title'],
                'detail' => (string) $row['detail'],
                'importance' => max(1, min(3, (int) $row['importance'])),
                'itemName' => $row['item_name'] === null ? null : (string) $row['item_name'],
                'guildName' => $row['guild_name'] === null ? null : (string) $row['guild_name'],
                'happenedAt' => (string) $row['happened_at'],
            ];
        }
        return $events;
    } catch (Throwable $exception) {
        error_log('Public player activity error: ' . $exception->getMessage());
        return [];
    }
}

/** @return array<string,mixed>|null */
function players_get_profile(PDO $pdo, int $playerId): ?array
{
    $statement = $pdo->prepare(
        'SELECT p.id, p.name, p.sexe AS sex, p.class AS class_id, p.level, p.xp, '
        . 'p.alignement AS alignment, p.alvl AS alignment_level, p.honor, p.logged, '
        . 'p.vitalite AS vitality, p.force AS strength, p.sagesse AS wisdom, '
        . 'p.intelligence, p.chance, p.agilite AS agility, p.pdvper AS health_percent, '
        . 'p.totalKills AS total_kills, p.deathCount AS death_count, p.objets, '
        . 'gm.guild AS guild_id, gm.rank AS guild_rank, gm.xpdone AS guild_xp, gm.pxp AS guild_xp_percent, '
        . 'g.name AS guild_name, g.lvl AS guild_level '
        . 'FROM world_players p '
        . 'LEFT JOIN starloco_game.guild_members gm ON gm.guid = p.id '
        . 'LEFT JOIN world_guilds g ON g.id = gm.guild '
        . 'WHERE p.id = :player_id AND p.groupe = 0 LIMIT 1'
    );
    $statement->bindValue(':player_id', $playerId, PDO::PARAM_INT);
    $statement->execute();
    $row = $statement->fetch(PDO::FETCH_ASSOC);
    if (!is_array($row)) {
        return null;
    }

    $class = players_class_meta((int) $row['class_id']);
    $alignment = players_alignment_meta((int) $row['alignment']);
    $kills = max(0, (int) $row['total_kills']);
    $deaths = max(0, (int) $row['death_count']);

    return [
        'id' => (int) $row['id'],
        'name' => (string) $row['name'],
        'sex' => (int) $row['sex'],
        'classId' => (int) $row['class_id'],
        'className' => $class['name'],
        'classGlyph' => $class['glyph'],
        'level' => max(1, (int) $row['level']),
        'xp' => max(0, (int) $row['xp']),
        'alignment' => $alignment,
        'alignmentLevel' => max(0, (int) $row['alignment_level']),
        'honor' => max(0, (int) $row['honor']),
        'online' => (int) $row['logged'] === 1,
        'healthPercent' => max(0, min(100, (int) $row['health_percent'])),
        'characteristics' => [
            ['name' => 'Vitalité', 'value' => max(0, (int) $row['vitality']), 'glyph' => '♥', 'tone' => 'vitality'],
            ['name' => 'Force', 'value' => max(0, (int) $row['strength']), 'glyph' => '◆', 'tone' => 'strength'],
            ['name' => 'Sagesse', 'value' => max(0, (int) $row['wisdom']), 'glyph' => '✦', 'tone' => 'wisdom'],
            ['name' => 'Intelligence', 'value' => max(0, (int) $row['intelligence']), 'glyph' => '▲', 'tone' => 'intelligence'],
            ['name' => 'Chance', 'value' => max(0, (int) $row['chance']), 'glyph' => '●', 'tone' => 'chance'],
            ['name' => 'Agilité', 'value' => max(0, (int) $row['agility']), 'glyph' => '✧', 'tone' => 'agility'],
        ],
        'combat' => [
            'kills' => $kills,
            'deaths' => $deaths,
            'ratio' => $deaths === 0 ? (float) $kills : round($kills / $deaths, 2),
        ],
        'guild' => $row['guild_id'] === null ? null : [
            'id' => (int) $row['guild_id'],
            'name' => $row['guild_name'] === null ? 'Guilde' : (string) $row['guild_name'],
            'level' => max(1, (int) ($row['guild_level'] ?? 1)),
            'rank' => players_guild_rank_name((int) ($row['guild_rank'] ?? 0)),
            'xpGiven' => max(0, (int) ($row['guild_xp'] ?? 0)),
            'xpPercent' => max(0, min(90, (int) ($row['guild_xp_percent'] ?? 0))),
        ],
        'equipment' => players_get_equipment($pdo, (string) $row['objets']),
        'quests' => players_get_quest_progress($pdo, $playerId),
        'activity' => players_get_recent_activity($pdo, $playerId),
    ];
}

function players_public_date(string $rawDate): string
{
    try {
        return (new DateTimeImmutable($rawDate, new DateTimeZone('UTC')))
            ->setTimezone(new DateTimeZone('Europe/Paris'))
            ->format('d/m/Y · H:i');
    } catch (Throwable) {
        return '';
    }
}
