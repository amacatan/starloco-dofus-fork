<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/admin.php';

app_security_headers();
admin_require_auth();
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit;
}

$csrf = is_scalar($_POST['csrf'] ?? null) ? (string) $_POST['csrf'] : '';
if (!admin_verify_csrf($csrf)) {
    http_response_code(403);
    admin_set_flash('error', 'Jeton de sécurité invalide. Rechargez la page.');
    header('Location: /admin/', true, 303);
    exit;
}

$action = is_scalar($_POST['action'] ?? null) ? (string) $_POST['action'] : 'unknown';
$playerId = 0;
$console = null;
try {
    admin_require_capability($action === 'ban_account' || $action === 'unban_account'
        ? 'accounts.moderate'
        : 'players.edit');
    $playerId = admin_integer($_POST['player_id'] ?? null, 1, PHP_INT_MAX, 'Personnage');
    $console = admin_console();

    $message = match ($action) {
        'set_kamas' => $console->setKamas(
            $playerId,
            admin_integer($_POST['kamas'] ?? null, 0, 1000000000, 'Kamas'),
        ),
        'adjust_kamas' => $console->adjustKamas(
            $playerId,
            admin_integer($_POST['delta'] ?? null, -1000000000, 1000000000, 'Ajustement'),
        ),
        'set_resources' => $console->setResources($playerId, [
            'capital' => admin_integer($_POST['capital'] ?? null, 0, 1000000, 'Points de caractéristiques'),
            'spellboost' => admin_integer($_POST['spellboost'] ?? null, 0, 1000000, 'Points de sorts'),
            'energy' => admin_integer($_POST['energy'] ?? null, 0, 10000, 'Énergie'),
            'vitalite' => admin_integer($_POST['vitalite'] ?? null, 0, 1000000, 'Vitalité'),
            'force' => admin_integer($_POST['force'] ?? null, 0, 1000000, 'Force'),
            'sagesse' => admin_integer($_POST['sagesse'] ?? null, 0, 1000000, 'Sagesse'),
            'intelligence' => admin_integer($_POST['intelligence'] ?? null, 0, 1000000, 'Intelligence'),
            'chance' => admin_integer($_POST['chance'] ?? null, 0, 1000000, 'Chance'),
            'agilite' => admin_integer($_POST['agilite'] ?? null, 0, 1000000, 'Agilité'),
        ]),
        'give_item' => $console->giveItem(
            $playerId,
            admin_integer($_POST['template_id'] ?? null, 1, PHP_INT_MAX, 'Template'),
            admin_integer($_POST['quantity'] ?? null, 1, 99999, 'Quantité'),
            is_scalar($_POST['quality'] ?? null) ? (string) $_POST['quality'] : 'base',
        ),
        'remove_item' => $console->removeItem(
            $playerId,
            admin_integer($_POST['object_id'] ?? null, 1, PHP_INT_MAX, 'Objet'),
            admin_integer($_POST['quantity'] ?? null, 1, 99999, 'Quantité'),
        ),
        'ban_account' => $console->setBan($playerId, true),
        'unban_account' => $console->setBan($playerId, false),
        default => throw new AdminOperationException('Action inconnue.'),
    };

    admin_set_flash('success', $message);
} catch (AdminOperationException $exception) {
    if ($console instanceof AdminConsole) {
        try {
            $console->recordFailure($action, $playerId, $exception->getMessage());
        } catch (Throwable $auditException) {
            error_log('Admin denied audit error: ' . $auditException->getMessage());
        }
    }
    admin_set_flash('error', $exception->getMessage());
} catch (Throwable $exception) {
    error_log('Admin action error [' . $action . ']: ' . $exception->getMessage());
    if ($console instanceof AdminConsole) {
        try {
            $console->recordFailure($action, $playerId, 'Erreur interne pendant la mutation');
        } catch (Throwable $auditException) {
            error_log('Admin failure audit error: ' . $auditException->getMessage());
        }
    }
    admin_set_flash('error', 'L’opération a été annulée sans modifier le personnage.');
}

admin_rotate_csrf();
$location = $playerId > 0 ? '/admin/player?id=' . $playerId : '/admin/';
header('Location: ' . $location, true, 303);
