<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/admin.php';

app_security_headers();
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit;
}

admin_require_auth();
$csrf = is_scalar($_POST['csrf'] ?? null) ? (string) $_POST['csrf'] : '';
if (!admin_verify_csrf($csrf)) {
    http_response_code(403);
    exit;
}
admin_logout();
header('Location: /admin/login', true, 303);

