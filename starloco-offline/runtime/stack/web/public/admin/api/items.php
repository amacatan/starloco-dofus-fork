<?php
declare(strict_types=1);

require_once dirname(__DIR__, 2) . '/app/admin.php';

app_security_headers();
admin_require_auth(true);
header('Content-Type: application/json; charset=utf-8');

$query = is_scalar($_GET['q'] ?? null) ? trim((string) $_GET['q']) : '';
if ($query === '' || mb_strlen($query) > 60) {
    echo json_encode(['ok' => true, 'items' => []], JSON_THROW_ON_ERROR);
    exit;
}

try {
    echo json_encode([
        'ok' => true,
        'items' => admin_console()->searchItems($query),
    ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
} catch (Throwable $exception) {
    error_log('Admin item search error: ' . $exception->getMessage());
    http_response_code(503);
    echo json_encode(['ok' => false, 'error' => 'Recherche momentanément indisponible.'], JSON_THROW_ON_ERROR);
}

