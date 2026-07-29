<?php
declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

final class SocialConfig
{
    public function __construct(
        public readonly int $scanIntervalSeconds,
        public readonly int $rareItemMinLevel,
        public readonly int $rareItemMinPrice,
        public readonly int $rareItemMinPoints,
        public readonly int $eventRetentionDays,
        public readonly int $maxStoredEvents,
    ) {}

    public static function fromEnvironment(): self
    {
        return new self(
            scanIntervalSeconds: self::integer('SOCIAL_SCAN_INTERVAL', 15, 5, 300),
            rareItemMinLevel: self::integer('SOCIAL_ITEM_MIN_LEVEL', 60, 1, 200),
            rareItemMinPrice: self::integer('SOCIAL_ITEM_MIN_PRICE', 50000, 0, 2000000000),
            rareItemMinPoints: self::integer('SOCIAL_ITEM_MIN_POINTS', 1, 0, 1000000),
            eventRetentionDays: self::integer('SOCIAL_RETENTION_DAYS', 90, 7, 3650),
            maxStoredEvents: self::integer('SOCIAL_MAX_EVENTS', 5000, 100, 100000),
        );
    }

    private static function integer(string $name, int $default, int $minimum, int $maximum): int
    {
        $raw = getenv($name);
        $value = $raw === false || $raw === '' ? $default : (int) $raw;
        return max($minimum, min($maximum, $value));
    }
}

/** @return array{name:string,glyph:string} */
function social_class_meta(int $classId): array
{
    return [
        1 => ['name' => 'Féca', 'glyph' => 'F'],
        2 => ['name' => 'Osamodas', 'glyph' => 'O'],
        3 => ['name' => 'Enutrof', 'glyph' => 'E'],
        4 => ['name' => 'Sram', 'glyph' => 'S'],
        5 => ['name' => 'Xélor', 'glyph' => 'X'],
        6 => ['name' => 'Écaflip', 'glyph' => 'É'],
        7 => ['name' => 'Eniripsa', 'glyph' => 'N'],
        8 => ['name' => 'Iop', 'glyph' => 'I'],
        9 => ['name' => 'Crâ', 'glyph' => 'C'],
        10 => ['name' => 'Sadida', 'glyph' => 'D'],
        11 => ['name' => 'Sacrieur', 'glyph' => 'R'],
        12 => ['name' => 'Pandawa', 'glyph' => 'P'],
    ][$classId] ?? ['name' => 'Aventurier', 'glyph' => '?'];
}

/** @return array{label:string,icon:string,category:string} */
function social_event_meta(string $type): array
{
    return [
        'character' => ['label' => 'Nouvel aventurier', 'icon' => '✦', 'category' => 'community'],
        'level' => ['label' => 'Progression', 'icon' => '↑', 'category' => 'progression'],
        'loot' => ['label' => 'Butin remarquable', 'icon' => '◆', 'category' => 'loot'],
        'quest' => ['label' => 'Quête accomplie', 'icon' => '✓', 'category' => 'progression'],
        'guild' => ['label' => 'Vie de guilde', 'icon' => '⚑', 'category' => 'guild'],
        'pvp' => ['label' => 'Alignement', 'icon' => '⚔', 'category' => 'progression'],
        'combat' => ['label' => 'Exploit de combat', 'icon' => '✹', 'category' => 'progression'],
        'system' => ['label' => 'Communauté', 'icon' => '●', 'category' => 'community'],
    ][$type] ?? ['label' => 'Activité', 'icon' => '•', 'category' => 'community'];
}

/** @return list<int> */
function social_parse_object_ids(string $raw): array
{
    if ($raw === '') {
        return [];
    }

    $parts = preg_split('/[^0-9]+/', $raw, -1, PREG_SPLIT_NO_EMPTY);
    if (!is_array($parts)) {
        return [];
    }

    $ids = [];
    foreach ($parts as $part) {
        $id = (int) $part;
        if ($id > 0) {
            $ids[$id] = $id;
        }
    }
    return array_values($ids);
}

function social_is_notable_item(array $item, SocialConfig $config): bool
{
    return (int) ($item['level'] ?? 0) >= $config->rareItemMinLevel
        || (int) ($item['avgPrice'] ?? 0) >= $config->rareItemMinPrice
        || (int) ($item['points'] ?? 0) >= $config->rareItemMinPoints;
}

/** @param array<string,mixed> $event */
function social_insert_event(PDO $pdo, array $event): bool
{
    $statement = $pdo->prepare(
        'INSERT IGNORE INTO website_social_events '
        . '(event_key, event_type, player_id, player_name, class_id, title, detail, importance, '
        . 'item_template_id, item_name, item_level, item_quantity, guild_name, old_value, new_value, happened_at) '
        . 'VALUES (:event_key, :event_type, :player_id, :player_name, :class_id, :title, :detail, :importance, '
        . ':item_template_id, :item_name, :item_level, :item_quantity, :guild_name, :old_value, :new_value, NOW())'
    );
    $statement->execute([
        'event_key' => (string) $event['event_key'],
        'event_type' => (string) $event['event_type'],
        'player_id' => $event['player_id'] ?? null,
        'player_name' => $event['player_name'] ?? null,
        'class_id' => $event['class_id'] ?? null,
        'title' => (string) $event['title'],
        'detail' => (string) ($event['detail'] ?? ''),
        'importance' => (int) ($event['importance'] ?? 1),
        'item_template_id' => $event['item_template_id'] ?? null,
        'item_name' => $event['item_name'] ?? null,
        'item_level' => $event['item_level'] ?? null,
        'item_quantity' => $event['item_quantity'] ?? null,
        'guild_name' => $event['guild_name'] ?? null,
        'old_value' => $event['old_value'] ?? null,
        'new_value' => $event['new_value'] ?? null,
    ]);
    return $statement->rowCount() > 0;
}

/**
 * Observe les tables du jeu et produit des événements sans modifier les données du serveur.
 * @return array{scanned_players:int,created_events:int,initialized:bool}
 */
function social_worker_tick(PDO $pdo, ?SocialConfig $config = null): array
{
    $config ??= SocialConfig::fromEnvironment();
    $lockName = 'starloco.social-feed.worker';
    $lock = $pdo->prepare('SELECT GET_LOCK(:name, 0)');
    $lock->execute(['name' => $lockName]);
    if ((int) $lock->fetchColumn() !== 1) {
        return ['scanned_players' => 0, 'created_events' => 0, 'initialized' => true];
    }

    $createdEvents = 0;
    try {
        $state = $pdo->query('SELECT initialized FROM website_social_state WHERE id = 1')->fetch(PDO::FETCH_ASSOC);
        if (!is_array($state)) {
            throw new RuntimeException('Migration du fil communautaire absente.');
        }
        $wasInitialized = (int) ($state['initialized'] ?? 0) === 1;

        $players = $pdo->query(
            'SELECT p.id, p.name, p.class AS class_id, p.level, p.xp, p.alignement AS alignment, '
            . 'p.alvl AS alignment_level, p.honor, p.logged, p.totalKills AS total_kills, '
            . 'p.deathCount AS death_count, p.objets, gm.guild AS guild_id, g.name AS guild_name '
            . 'FROM world_players p '
            . 'LEFT JOIN starloco_game.guild_members gm ON gm.guid = p.id '
            . 'LEFT JOIN world_guilds g ON g.id = gm.guild '
            . 'WHERE p.groupe = 0 ORDER BY p.id'
        )->fetchAll(PDO::FETCH_ASSOC);

        $snapshotRows = $pdo->query('SELECT * FROM website_social_player_snapshots')->fetchAll(PDO::FETCH_ASSOC);
        $snapshots = [];
        foreach ($snapshotRows as $row) {
            $snapshots[(int) $row['player_id']] = $row;
        }

        $inventoryRows = $pdo->query('SELECT player_id, object_id, template_id, quantity FROM website_social_inventory_snapshots')->fetchAll(PDO::FETCH_ASSOC);
        $oldInventory = [];
        foreach ($inventoryRows as $row) {
            $oldInventory[(int) $row['player_id']][(int) $row['object_id']] = $row;
        }

        $questRows = $pdo->query('SELECT player_id, quest_id FROM website_social_quest_snapshots')->fetchAll(PDO::FETCH_ASSOC);
        $oldQuests = [];
        foreach ($questRows as $row) {
            $oldQuests[(int) $row['player_id']][(int) $row['quest_id']] = true;
        }

        $currentInventoryIds = [];
        $allObjectIds = [];
        foreach ($players as $player) {
            $playerId = (int) $player['id'];
            $ids = social_parse_object_ids((string) ($player['objets'] ?? ''));
            $currentInventoryIds[$playerId] = $ids;
            foreach ($ids as $id) {
                $allObjectIds[$id] = $id;
            }
        }

        $objectDetails = [];
        foreach (array_chunk(array_values($allObjectIds), 500) as $chunk) {
            if ($chunk === []) {
                continue;
            }
            $placeholders = implode(',', array_fill(0, count($chunk), '?'));
            $statement = $pdo->prepare(
                'SELECT o.id AS object_id, o.template AS template_id, o.quantity, '
                . 't.name, t.level, t.avgPrice, t.points '
                . 'FROM world_objects o '
                . 'LEFT JOIN starloco_game.item_template t ON t.id = o.template '
                . "WHERE o.id IN ($placeholders)"
            );
            $statement->execute($chunk);
            foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
                $objectDetails[(int) $row['object_id']] = $row;
            }
        }

        $completedQuests = [];
        $finished = $pdo->query(
            'SELECT completed.player, completed.quest, COALESCE(q.nom, \'Quête accomplie\') AS quest_name '
            . 'FROM ('
            . 'SELECT qp.player_id AS player, qp.quest_id AS quest '
            . 'FROM starloco_game.quest_progress qp WHERE qp.finished = 1 AND qp.player_id > 0 '
            . 'UNION '
            . 'SELECT legacy.player, legacy.quest FROM world_players_quests legacy WHERE legacy.finish = 1'
            . ') completed '
            . 'LEFT JOIN starloco_game.quest q ON q.id = completed.quest '
            . 'INNER JOIN world_players p ON p.id = completed.player AND p.groupe = 0'
        )->fetchAll(PDO::FETCH_ASSOC);
        foreach ($finished as $row) {
            $completedQuests[(int) $row['player']][(int) $row['quest']] = (string) $row['quest_name'];
        }

        $pdo->beginTransaction();
        $upsertPlayer = $pdo->prepare(
            'INSERT INTO website_social_player_snapshots '
            . '(player_id, player_name, class_id, level, xp, alignment, alignment_level, honor, guild_id, guild_name, logged, total_kills, death_count, first_seen_at, last_seen_at) '
            . 'VALUES (:player_id, :player_name, :class_id, :level, :xp, :alignment, :alignment_level, :honor, :guild_id, :guild_name, :logged, :total_kills, :death_count, NOW(), NOW()) '
            . 'ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), class_id=VALUES(class_id), level=VALUES(level), xp=VALUES(xp), '
            . 'alignment=VALUES(alignment), alignment_level=VALUES(alignment_level), honor=VALUES(honor), guild_id=VALUES(guild_id), '
            . 'guild_name=VALUES(guild_name), logged=VALUES(logged), total_kills=VALUES(total_kills), death_count=VALUES(death_count), last_seen_at=NOW()'
        );
        $deleteInventory = $pdo->prepare('DELETE FROM website_social_inventory_snapshots WHERE player_id = ?');
        $insertInventory = $pdo->prepare(
            'INSERT INTO website_social_inventory_snapshots (player_id, object_id, template_id, quantity, first_seen_at, last_seen_at) '
            . 'VALUES (?, ?, ?, ?, NOW(), NOW())'
        );
        $insertQuest = $pdo->prepare(
            'INSERT IGNORE INTO website_social_quest_snapshots (player_id, quest_id, quest_name, first_seen_at) VALUES (?, ?, ?, NOW())'
        );

        foreach ($players as $player) {
            $playerId = (int) $player['id'];
            $name = (string) $player['name'];
            $classId = (int) $player['class_id'];
            $level = (int) $player['level'];
            $old = $snapshots[$playerId] ?? null;

            if ($wasInitialized && $old === null) {
                $createdEvents += (int) social_insert_event($pdo, [
                    'event_key' => "character:$playerId",
                    'event_type' => 'character',
                    'player_id' => $playerId,
                    'player_name' => $name,
                    'class_id' => $classId,
                    'title' => "$name commence son aventure",
                    'detail' => social_class_meta($classId)['name'] . " de niveau $level",
                    'importance' => 1,
                    'new_value' => $level,
                ]);
            }

            if ($old !== null) {
                $oldLevel = (int) $old['level'];
                if ($level > $oldLevel) {
                    $importance = $level >= 200 ? 3 : (($level % 20 === 0 || $level >= 100) ? 2 : 1);
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => "level:$playerId:$level",
                        'event_type' => 'level',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => "$name atteint le niveau $level",
                        'detail' => $level >= 200 ? 'Le niveau ultime est atteint.' : social_class_meta($classId)['name'] . ' poursuit sa progression.',
                        'importance' => $importance,
                        'old_value' => $oldLevel,
                        'new_value' => $level,
                    ]);
                }

                $oldAlignmentLevel = (int) $old['alignment_level'];
                $alignmentLevel = (int) $player['alignment_level'];
                if ($alignmentLevel > $oldAlignmentLevel && $alignmentLevel > 0) {
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => "pvp:$playerId:$alignmentLevel",
                        'event_type' => 'pvp',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => "$name progresse dans son alignement",
                        'detail' => "Rang d’alignement $alignmentLevel",
                        'importance' => $alignmentLevel >= 8 ? 2 : 1,
                        'old_value' => $oldAlignmentLevel,
                        'new_value' => $alignmentLevel,
                    ]);
                }

                $oldGuildId = $old['guild_id'] === null ? 0 : (int) $old['guild_id'];
                $newGuildId = $player['guild_id'] === null ? 0 : (int) $player['guild_id'];
                if ($oldGuildId !== $newGuildId) {
                    $guildName = trim((string) ($player['guild_name'] ?? ''));
                    if ($newGuildId > 0) {
                        $title = $oldGuildId > 0 ? "$name change de guilde" : "$name rejoint une guilde";
                        $detail = $guildName !== '' ? "Bienvenue chez $guildName." : 'Une nouvelle aventure collective commence.';
                    } else {
                        $title = "$name quitte sa guilde";
                        $detail = 'Le personnage poursuit désormais sa route en solitaire.';
                    }
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => 'guild:' . $playerId . ':' . $oldGuildId . ':' . $newGuildId . ':' . bin2hex(random_bytes(5)),
                        'event_type' => 'guild',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => $title,
                        'detail' => $detail,
                        'importance' => 1,
                        'guild_name' => $guildName !== '' ? $guildName : null,
                        'old_value' => $oldGuildId,
                        'new_value' => $newGuildId,
                    ]);
                }

                $oldKills = (int) $old['total_kills'];
                $newKills = (int) $player['total_kills'];
                $oldMilestone = intdiv(max(0, $oldKills), 100);
                $newMilestone = intdiv(max(0, $newKills), 100);
                if ($newMilestone > $oldMilestone) {
                    $milestone = $newMilestone * 100;
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => "combat:$playerId:$milestone",
                        'event_type' => 'combat',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => "$name franchit le cap des $milestone victoires",
                        'detail' => 'Un nouveau palier de combat est inscrit au tableau.',
                        'importance' => $milestone >= 1000 ? 2 : 1,
                        'old_value' => $oldKills,
                        'new_value' => $newKills,
                    ]);
                }
            }

            $currentIds = $currentInventoryIds[$playerId] ?? [];
            $previousItems = $oldInventory[$playerId] ?? [];
            foreach ($currentIds as $objectId) {
                $item = $objectDetails[$objectId] ?? [
                    'object_id' => $objectId,
                    'template_id' => 0,
                    'quantity' => 1,
                    'name' => null,
                    'level' => 0,
                    'avgPrice' => 0,
                    'points' => 0,
                ];
                $newQuantity = max(1, (int) ($item['quantity'] ?? 1));
                $oldQuantity = isset($previousItems[$objectId]) ? (int) $previousItems[$objectId]['quantity'] : 0;
                $gainedQuantity = max(0, $newQuantity - $oldQuantity);
                $itemName = trim((string) ($item['name'] ?? ''));

                if ($wasInitialized && $gainedQuantity > 0 && $itemName !== '' && social_is_notable_item($item, $config)) {
                    $itemLevel = (int) ($item['level'] ?? 0);
                    $importance = $itemLevel >= 180 || (int) ($item['points'] ?? 0) > 0 ? 3 : ($itemLevel >= 100 ? 2 : 1);
                    $quantityText = $gainedQuantity > 1 ? " ×$gainedQuantity" : '';
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => "loot:$playerId:$objectId:$newQuantity",
                        'event_type' => 'loot',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => "$name a obtenu $itemName$quantityText",
                        'detail' => $itemLevel > 0 ? "Objet de niveau $itemLevel" : 'Objet remarquable ajouté à l’inventaire.',
                        'importance' => $importance,
                        'item_template_id' => (int) ($item['template_id'] ?? 0),
                        'item_name' => $itemName,
                        'item_level' => $itemLevel,
                        'item_quantity' => $gainedQuantity,
                        'old_value' => $oldQuantity,
                        'new_value' => $newQuantity,
                    ]);
                }
            }

            foreach (($completedQuests[$playerId] ?? []) as $questId => $questName) {
                if ($wasInitialized && !isset($oldQuests[$playerId][$questId])) {
                    $createdEvents += (int) social_insert_event($pdo, [
                        'event_key' => "quest:$playerId:$questId",
                        'event_type' => 'quest',
                        'player_id' => $playerId,
                        'player_name' => $name,
                        'class_id' => $classId,
                        'title' => "$name termine une quête",
                        'detail' => $questName,
                        'importance' => 1,
                        'new_value' => $questId,
                    ]);
                }
                $insertQuest->execute([$playerId, $questId, $questName]);
            }

            $upsertPlayer->execute([
                'player_id' => $playerId,
                'player_name' => $name,
                'class_id' => $classId,
                'level' => $level,
                'xp' => (int) $player['xp'],
                'alignment' => (int) $player['alignment'],
                'alignment_level' => (int) $player['alignment_level'],
                'honor' => (int) $player['honor'],
                'guild_id' => $player['guild_id'] === null ? null : (int) $player['guild_id'],
                'guild_name' => ($player['guild_name'] ?? null) === null ? null : (string) $player['guild_name'],
                'logged' => (int) $player['logged'],
                'total_kills' => (int) $player['total_kills'],
                'death_count' => (int) $player['death_count'],
            ]);

            $deleteInventory->execute([$playerId]);
            foreach ($currentIds as $objectId) {
                $item = $objectDetails[$objectId] ?? null;
                $insertInventory->execute([
                    $playerId,
                    $objectId,
                    $item === null ? 0 : (int) $item['template_id'],
                    $item === null ? 1 : max(1, (int) $item['quantity']),
                ]);
            }
        }

        $stateUpdate = $pdo->prepare(
            'UPDATE website_social_state SET initialized = 1, initialized_at = COALESCE(initialized_at, NOW()), '
            . 'last_scan_at = NOW(), heartbeat_at = NOW(), last_error = NULL WHERE id = 1'
        );
        $stateUpdate->execute();

        $retention = $config->eventRetentionDays;
        $pdo->exec("DELETE FROM website_social_events WHERE happened_at < (NOW() - INTERVAL $retention DAY) AND event_type <> 'system'");
        $max = $config->maxStoredEvents;
        $pdo->exec(
            'DELETE FROM website_social_events WHERE id IN ('
            . "SELECT id FROM (SELECT id FROM website_social_events ORDER BY id DESC LIMIT 18446744073709551615 OFFSET $max) old_events"
            . ')'
        );

        $pdo->commit();
        return [
            'scanned_players' => count($players),
            'created_events' => $createdEvents,
            'initialized' => $wasInitialized,
        ];
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        try {
            $statement = $pdo->prepare('UPDATE website_social_state SET heartbeat_at = NOW(), last_error = :error WHERE id = 1');
            $statement->execute(['error' => mb_substr($exception->getMessage(), 0, 255)]);
        } catch (Throwable) {
            // La migration peut ne pas encore être appliquée pendant le premier démarrage.
        }
        throw $exception;
    } finally {
        try {
            $release = $pdo->prepare('SELECT RELEASE_LOCK(:name)');
            $release->execute(['name' => $lockName]);
        } catch (Throwable) {
        }
    }
}

/** @return list<string> */
function social_filter_types(string $filter): array
{
    return match ($filter) {
        'progression' => ['level', 'quest', 'pvp', 'combat'],
        'loot' => ['loot'],
        'guild' => ['guild'],
        'community' => ['character', 'system'],
        default => [],
    };
}

/** @return list<array<string,mixed>> */
function social_get_events(PDO $pdo, string $filter = 'all', int $limit = 25, ?int $before = null, ?int $after = null): array
{
    $limit = max(1, min(50, $limit));
    $conditions = ['visible = 1'];
    $params = [];
    $types = social_filter_types($filter);
    if ($types !== []) {
        $placeholders = [];
        foreach ($types as $index => $type) {
            $key = 'type' . $index;
            $placeholders[] = ':' . $key;
            $params[$key] = $type;
        }
        $conditions[] = 'event_type IN (' . implode(',', $placeholders) . ')';
    }
    if ($before !== null && $before > 0) {
        $conditions[] = 'id < :before';
        $params['before'] = $before;
    }
    if ($after !== null && $after > 0) {
        $conditions[] = 'id > :after';
        $params['after'] = $after;
    }

    $ascending = $after !== null && $after > 0;
    $sql = 'SELECT id, event_type, player_id, player_name, class_id, title, detail, importance, '
        . 'item_template_id, item_name, item_level, item_quantity, guild_name, old_value, new_value, happened_at '
        . 'FROM website_social_events WHERE ' . implode(' AND ', $conditions)
        . ' ORDER BY id ' . ($ascending ? 'ASC' : 'DESC') . " LIMIT $limit";
    $statement = $pdo->prepare($sql);
    $statement->execute($params);

    $events = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $meta = social_event_meta((string) $row['event_type']);
        $class = social_class_meta((int) ($row['class_id'] ?? 0));
        $events[] = [
            'id' => (int) $row['id'],
            'type' => (string) $row['event_type'],
            'category' => $meta['category'],
            'label' => $meta['label'],
            'icon' => $meta['icon'],
            'playerId' => $row['player_id'] === null ? null : (int) $row['player_id'],
            'playerName' => $row['player_name'] === null ? null : (string) $row['player_name'],
            'classId' => $row['class_id'] === null ? null : (int) $row['class_id'],
            'className' => $class['name'],
            'classGlyph' => $class['glyph'],
            'title' => (string) $row['title'],
            'detail' => (string) $row['detail'],
            'importance' => (int) $row['importance'],
            'itemName' => $row['item_name'] === null ? null : (string) $row['item_name'],
            'itemLevel' => $row['item_level'] === null ? null : (int) $row['item_level'],
            'itemQuantity' => $row['item_quantity'] === null ? null : (int) $row['item_quantity'],
            'guildName' => $row['guild_name'] === null ? null : (string) $row['guild_name'],
            'happenedAt' => str_replace(' ', 'T', (string) $row['happened_at']) . 'Z',
        ];
    }
    return $events;
}

/** @return array<string,mixed> */
function social_get_dashboard(PDO $pdo): array
{
    $stats = $pdo->query(
        'SELECT COUNT(*) AS characters, COALESCE(SUM(logged = 1), 0) AS online, '
        . 'COALESCE(MAX(level), 0) AS max_level, COALESCE(SUM(level >= 200), 0) AS level_200 '
        . 'FROM world_players WHERE groupe = 0'
    )->fetch(PDO::FETCH_ASSOC);
    $guilds = (int) $pdo->query('SELECT COUNT(*) FROM world_guilds')->fetchColumn();
    $eventsToday = (int) $pdo->query(
        'SELECT COUNT(*) FROM website_social_events WHERE visible = 1 AND happened_at >= CURDATE()'
    )->fetchColumn();
    $state = $pdo->query(
        'SELECT initialized, heartbeat_at, last_scan_at, '
        . 'heartbeat_at >= (NOW() - INTERVAL 90 SECOND) AS worker_online '
        . 'FROM website_social_state WHERE id = 1'
    )->fetch(PDO::FETCH_ASSOC);

    $leaders = $pdo->query(
        'SELECT id, name, class AS class_id, level, xp, logged '
        . 'FROM world_players WHERE groupe = 0 ORDER BY level DESC, xp DESC, id ASC LIMIT 6'
    )->fetchAll(PDO::FETCH_ASSOC);
    $leaderboard = [];
    foreach ($leaders as $position => $leader) {
        $class = social_class_meta((int) $leader['class_id']);
        $leaderboard[] = [
            'position' => $position + 1,
            'id' => (int) $leader['id'],
            'name' => (string) $leader['name'],
            'className' => $class['name'],
            'classGlyph' => $class['glyph'],
            'level' => (int) $leader['level'],
            'online' => (int) $leader['logged'] === 1,
        ];
    }

    return [
        'stats' => [
            'characters' => (int) ($stats['characters'] ?? 0),
            'online' => (int) ($stats['online'] ?? 0),
            'maxLevel' => (int) ($stats['max_level'] ?? 0),
            'level200' => (int) ($stats['level_200'] ?? 0),
            'guilds' => $guilds,
            'eventsToday' => $eventsToday,
        ],
        'worker' => [
            'initialized' => is_array($state) && (int) ($state['initialized'] ?? 0) === 1,
            'online' => is_array($state) && (int) ($state['worker_online'] ?? 0) === 1,
            'heartbeatAt' => is_array($state) ? ($state['heartbeat_at'] ?? null) : null,
            'lastScanAt' => is_array($state) ? ($state['last_scan_at'] ?? null) : null,
        ],
        'leaderboard' => $leaderboard,
    ];
}
