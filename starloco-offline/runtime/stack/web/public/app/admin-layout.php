<?php
declare(strict_types=1);

require_once __DIR__ . '/admin.php';

function admin_format_number(int|float $value): string
{
    return number_format($value, 0, ',', ' ');
}

function admin_action_label(string $action): string
{
    return [
        'set_kamas' => 'Portefeuille',
        'adjust_kamas' => 'Ajustement',
        'set_resources' => 'Caractéristiques',
        'give_item' => 'Objet ajouté',
        'remove_item' => 'Objet retiré',
        'ban_account' => 'Compte banni',
        'unban_account' => 'Compte débanni',
    ][$action] ?? $action;
}

function admin_render_head(string $title, string $bodyClass = 'admin-page'): void
{
    $config = AppConfig::fromEnvironment();
    ?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="dark">
    <title><?= app_escape($title) ?> · <?= app_escape($config->serverName) ?> Control</title>
    <link rel="stylesheet" href="/assets/app.css">
    <link rel="stylesheet" href="/assets/admin.css">
</head>
<body class="<?= app_escape($bodyClass) ?>">
    <?php
}

function admin_render_shell_start(string $title, string $subtitle, string $active = 'dashboard'): void
{
    admin_render_head($title);
    $config = AppConfig::fromEnvironment();
    ?>
<div class="admin-app">
    <aside class="admin-sidebar">
        <a class="control-brand" href="/admin/">
            <span class="control-brand-mark">S</span>
            <span><strong>CONTROL</strong><small><?= app_escape($config->serverName) ?></small></span>
        </a>
        <nav class="admin-nav" aria-label="Administration">
            <a href="/admin/"<?= $active === 'dashboard' ? ' class="active" aria-current="page"' : '' ?>>
                <span aria-hidden="true">⌁</span><span>Vue d’ensemble</span>
            </a>
            <a href="/admin/#characters"<?= $active === 'characters' ? ' class="active" aria-current="page"' : '' ?>>
                <span aria-hidden="true">◇</span><span>Personnages</span>
            </a>
            <a href="/admin/#audit"<?= $active === 'audit' ? ' class="active" aria-current="page"' : '' ?>>
                <span aria-hidden="true">≡</span><span>Journal d’audit</span>
            </a>
        </nav>
        <div class="admin-sidebar-bottom">
            <a class="admin-public-link" href="/" target="_blank" rel="noopener">
                Voir le portail <span aria-hidden="true">↗</span>
            </a>
            <div class="admin-identity">
                <span class="identity-orb" aria-hidden="true"></span>
                <span><strong><?= app_escape(admin_actor()) ?></strong><small>Propriétaire</small></span>
            </div>
            <form method="post" action="/admin/logout">
                <input type="hidden" name="csrf" value="<?= app_escape(admin_csrf_token()) ?>">
                <button class="logout-button" type="submit">Se déconnecter</button>
            </form>
        </div>
    </aside>
    <div class="admin-workspace">
        <header class="admin-topbar">
            <div>
                <p class="admin-kicker"><span></span> Console sécurisée</p>
                <h1><?= app_escape($title) ?></h1>
                <p><?= app_escape($subtitle) ?></p>
            </div>
            <div class="admin-top-actions">
                <a class="ghost-action" href="/players" target="_blank" rel="noopener">Annuaire public</a>
                <span class="system-badge"><i></i> Système prêt</span>
            </div>
        </header>
        <main class="admin-content">
    <?php
}

function admin_render_flash(?array $flash): void
{
    if ($flash === null) {
        return;
    }
    $type = in_array($flash['type'], ['success', 'error', 'warning'], true) ? $flash['type'] : 'warning';
    ?>
    <div class="admin-flash <?= app_escape($type) ?>" role="status">
        <span aria-hidden="true"><?= $type === 'success' ? '✓' : ($type === 'error' ? '!' : 'i') ?></span>
        <p><?= app_escape($flash['message']) ?></p>
        <button type="button" data-dismiss-flash aria-label="Fermer">×</button>
    </div>
    <?php
}

function admin_render_shell_end(): void
{
    ?>
        </main>
    </div>
</div>
<script src="/assets/admin.js" defer></script>
</body>
</html>
    <?php
}

