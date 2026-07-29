<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    exit(1);
}

require_once __DIR__ . '/app/bootstrap.php';

try {
    $pdo = app_pdo();
    $healthy = (int) $pdo->query(
        'SELECT COUNT(*) FROM website_social_state '
        . 'WHERE id = 1 AND last_error IS NULL AND last_scan_at IS NOT NULL AND heartbeat_at >= (NOW() - INTERVAL 90 SECOND)'
    )->fetchColumn() === 1;
    exit($healthy ? 0 : 1);
} catch (Throwable) {
    exit(1);
}
