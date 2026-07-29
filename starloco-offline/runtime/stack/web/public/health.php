<?php
declare(strict_types=1);
require_once __DIR__ . '/app/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$deep = isset($_GET['deep']) && $_GET['deep'] === '1';
if (!$deep) {
    echo json_encode(['status' => 'ok', 'service' => 'community-portal'], JSON_THROW_ON_ERROR);
    exit;
}

$remote = is_scalar($_SERVER['REMOTE_ADDR'] ?? null) ? (string) $_SERVER['REMOTE_ADDR'] : '';
if (!in_array($remote, ['127.0.0.1', '::1'], true)) {
    http_response_code(403);
    echo json_encode(['status' => 'forbidden'], JSON_THROW_ON_ERROR);
    exit;
}

try {
    $pdo = app_pdo();
    $pdo->query('SELECT 1')->fetchColumn();

    $table = $pdo->query(
        "SELECT ENGINE, TABLE_COLLATION
         FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'world_accounts'"
    )->fetch(PDO::FETCH_ASSOC);
    if (!is_array($table) || ($table['ENGINE'] ?? null) !== 'InnoDB') {
        throw new RuntimeException('Table world_accounts absente ou non transactionnelle.');
    }

    $rows = $pdo->query(
        "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'world_accounts'"
    )->fetchAll(PDO::FETCH_ASSOC);

    $columns = [];
    foreach ($rows as $row) {
        $columns[(string) $row['COLUMN_NAME']] = $row;
    }

    $requirements = [
        'guid' => null,
        'account' => ['varchar', 30, 'NO'],
        'pass' => ['char', 128, 'NO'],
        'email' => ['varchar', 100, 'NO'],
        'question' => ['varchar', 100, 'NO'],
        'reponse' => ['varchar', 100, 'NO'],
        'pseudo' => ['varchar', 30, 'NO'],
        'dateRegister' => ['varchar', 10, null],
        'reload_needed' => null,
        'logged' => null,
    ];

    foreach ($requirements as $name => $expected) {
        if (!isset($columns[$name])) {
            throw new RuntimeException("Colonne manquante : $name");
        }
        if ($expected === null) {
            continue;
        }
        [$type, $length, $nullable] = $expected;
        $column = $columns[$name];
        if (
            strtolower((string) $column['DATA_TYPE']) !== $type
            || (int) $column['CHARACTER_MAXIMUM_LENGTH'] !== $length
            || ($nullable !== null && (string) $column['IS_NULLABLE'] !== $nullable)
        ) {
            throw new RuntimeException("Colonne incohérente : $name");
        }
    }

    $duplicates = $pdo->query(
        "SELECT
           (SELECT COUNT(*) FROM (
             SELECT LOWER(TRIM(email))
             FROM world_accounts
             GROUP BY LOWER(TRIM(email))
             HAVING COUNT(*) > 1
           ) duplicate_emails) AS duplicate_emails,
           (SELECT COUNT(*) FROM (
             SELECT LOWER(TRIM(pseudo))
             FROM world_accounts
             GROUP BY LOWER(TRIM(pseudo))
             HAVING COUNT(*) > 1
           ) duplicate_pseudos) AS duplicate_pseudos"
    )->fetch(PDO::FETCH_ASSOC);

    if (
        !is_array($duplicates)
        || (int) ($duplicates['duplicate_emails'] ?? 0) > 0
        || (int) ($duplicates['duplicate_pseudos'] ?? 0) > 0
    ) {
        throw new RuntimeException('Doublons historiques dans world_accounts.');
    }


    $socialTables = (int) $pdo->query(
        "SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME IN ('website_social_events', 'website_social_player_snapshots',
                              'website_social_inventory_snapshots', 'website_social_quest_snapshots',
                              'website_social_state')"
    )->fetchColumn();
    if ($socialTables !== 5) {
        throw new RuntimeException('Migration du fil communautaire absente.');
    }

    $adminStorage = $pdo->query(
        "SELECT
           (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME IN ('world_players', 'world_objects')
              AND ENGINE = 'InnoDB') AS transactional_tables,
           (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'world_objects'
              AND INDEX_NAME = 'PRIMARY') AS object_primary_key,
           (SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME IN ('website_admin_audit', 'website_admin_login_attempts')
              AND ENGINE = 'InnoDB') AS admin_tables"
    )->fetch(PDO::FETCH_ASSOC);
    if (
        !is_array($adminStorage)
        || (int) ($adminStorage['transactional_tables'] ?? 0) !== 2
        || (int) ($adminStorage['object_primary_key'] ?? 0) < 1
        || (int) ($adminStorage['admin_tables'] ?? 0) !== 2
    ) {
        throw new RuntimeException('Migration de la console administrateur absente ou non transactionnelle.');
    }

    echo json_encode([
        'status' => 'ok',
        'database' => 'ready',
        'engine' => 'InnoDB',
        'schema' => 'coherent',
        'social_feed' => 'ready',
        'admin_console' => 'ready',
    ], JSON_THROW_ON_ERROR);
} catch (Throwable $exception) {
    error_log('Health check failed: ' . $exception->getMessage());
    http_response_code(503);
    echo json_encode([
        'status' => 'error',
        'database' => 'unavailable_or_incoherent',
    ], JSON_THROW_ON_ERROR);
}
