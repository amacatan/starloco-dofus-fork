<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/app/social.php';

app_security_headers();
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$allowedFilters = ['all', 'progression', 'loot', 'guild', 'community'];
$filter = is_scalar($_GET['filter'] ?? null) ? (string) $_GET['filter'] : 'all';
if (!in_array($filter, $allowedFilters, true)) {
    $filter = 'all';
}
$before = filter_input(INPUT_GET, 'before', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]) ?: null;
$after = filter_input(INPUT_GET, 'after', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]) ?: null;
$limit = filter_input(INPUT_GET, 'limit', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 50]]) ?: 25;

try {
    $pdo = app_pdo();
    $events = social_get_events($pdo, $filter, $limit, $before, $after);
    $dashboard = social_get_dashboard($pdo);
    echo json_encode([
        'ok' => true,
        'events' => $events,
        'dashboard' => $dashboard,
        'serverTime' => gmdate(DATE_ATOM),
    ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
} catch (Throwable $exception) {
    error_log('Feed API error: ' . $exception->getMessage());
    http_response_code(503);
    echo json_encode([
        'ok' => false,
        'error' => 'Le fil communautaire est temporairement indisponible.',
    ], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE);
}
