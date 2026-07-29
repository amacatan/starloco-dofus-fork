<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/admin-layout.php';

app_security_headers();
admin_require_auth();

$query = is_scalar($_GET['q'] ?? null) ? trim((string) $_GET['q']) : '';
$stats = ['characters' => 0, 'online' => 0, 'totalKamas' => 0, 'accounts' => 0, 'banned' => 0];
$players = [];
$audit = [];
$loadError = null;
try {
    $console = admin_console();
    $stats = $console->stats();
    $players = $console->findPlayers($query);
    $audit = $console->recentAudit(16);
} catch (Throwable $exception) {
    error_log('Admin dashboard error: ' . $exception->getMessage());
    $loadError = 'Impossible de charger les données de la console.';
}

admin_render_shell_start('Vue d’ensemble', 'Pilotez votre serveur sans toucher directement à la base.');
admin_render_flash(admin_take_flash());
if ($loadError !== null): ?>
    <div class="admin-flash error" role="alert"><span>!</span><p><?= app_escape($loadError) ?></p></div>
<?php endif; ?>

<section class="admin-metrics" aria-label="Indicateurs">
    <article class="admin-metric primary">
        <span class="metric-icon">◇</span>
        <div><small>Personnages</small><strong><?= admin_format_number($stats['characters']) ?></strong></div>
        <span class="metric-trend"><?= admin_format_number($stats['online']) ?> en ligne</span>
    </article>
    <article class="admin-metric">
        <span class="metric-icon">◎</span>
        <div><small>Comptes</small><strong><?= admin_format_number($stats['accounts']) ?></strong></div>
        <span class="metric-trend"><?= admin_format_number($stats['banned']) ?> banni<?= $stats['banned'] > 1 ? 's' : '' ?></span>
    </article>
    <article class="admin-metric wide-number">
        <span class="metric-icon">K</span>
        <div><small>Kamas en circulation</small><strong><?= admin_format_number($stats['totalKamas']) ?></strong></div>
        <span class="metric-trend">Économie globale</span>
    </article>
    <article class="admin-metric secure">
        <span class="metric-icon">✓</span>
        <div><small>Mode d’édition</small><strong>Atomique</strong></div>
        <span class="metric-trend">Audit activé</span>
    </article>
</section>

<section class="admin-panel character-panel" id="characters">
    <div class="admin-panel-heading">
        <div>
            <p class="panel-overline">Population</p>
            <h2>Personnages</h2>
            <p>Recherchez par personnage, compte ou pseudo.</p>
        </div>
        <form class="admin-search" method="get" action="/admin/">
            <span aria-hidden="true">⌕</span>
            <input type="search" name="q" value="<?= app_escape($query) ?>" maxlength="50"
                   placeholder="Rechercher un aventurier…" aria-label="Rechercher">
            <button type="submit">Rechercher</button>
        </form>
    </div>

    <div class="admin-table-wrap">
        <table class="admin-table">
            <thead>
            <tr><th>Personnage</th><th>Compte</th><th>Niveau</th><th>Kamas</th><th>État</th><th><span class="sr-only">Ouvrir</span></th></tr>
            </thead>
            <tbody>
            <?php foreach ($players as $player): ?>
                <tr>
                    <td>
                        <a class="admin-character" href="/admin/player?id=<?= (int) $player['id'] ?>">
                            <span class="admin-avatar class-<?= (int) $player['class_id'] ?>"><?= app_escape((string) $player['classGlyph']) ?></span>
                            <span><strong><?= app_escape((string) $player['name']) ?></strong><small><?= app_escape((string) $player['className']) ?><?= $player['guild_name'] ? ' · ' . app_escape((string) $player['guild_name']) : '' ?></small></span>
                        </a>
                    </td>
                    <td><strong><?= app_escape((string) $player['account_name']) ?></strong><small><?= app_escape((string) $player['pseudo']) ?></small></td>
                    <td><span class="level-chip">Niv. <?= (int) $player['level'] ?></span></td>
                    <td class="numeric"><?= admin_format_number((int) $player['kamas']) ?> K</td>
                    <td>
                        <?php if ($player['online']): ?>
                            <span class="admin-status online"><i></i> En ligne</span>
                        <?php elseif ((int) $player['banned'] === 1): ?>
                            <span class="admin-status banned"><i></i> Banni</span>
                        <?php else: ?>
                            <span class="admin-status offline"><i></i> Hors ligne</span>
                        <?php endif; ?>
                    </td>
                    <td><a class="row-arrow" href="/admin/player?id=<?= (int) $player['id'] ?>" aria-label="Éditer <?= app_escape((string) $player['name']) ?>">→</a></td>
                </tr>
            <?php endforeach; ?>
            <?php if ($players === []): ?>
                <tr><td colspan="6"><div class="admin-empty"><span>⌕</span><strong>Aucun personnage trouvé</strong><p>Essayez un autre nom ou créez votre premier aventurier.</p></div></td></tr>
            <?php endif; ?>
            </tbody>
        </table>
    </div>
</section>

<section class="admin-panel audit-panel" id="audit">
    <div class="admin-panel-heading">
        <div><p class="panel-overline">Traçabilité</p><h2>Dernières actions</h2><p>Chaque mutation réussie ou refusée reste identifiable.</p></div>
        <span class="immutable-badge">Append-only</span>
    </div>
    <div class="audit-list">
        <?php foreach ($audit as $entry): ?>
            <article class="audit-entry <?= app_escape((string) $entry['outcome']) ?>">
                <span class="audit-symbol"><?= (string) $entry['outcome'] === 'success' ? '✓' : '!' ?></span>
                <div>
                    <div class="audit-title"><strong><?= app_escape(admin_action_label((string) $entry['action'])) ?></strong><span><?= app_escape((string) $entry['summary']) ?></span></div>
                    <p><?= $entry['player_name'] ? app_escape((string) $entry['player_name']) . ' · ' : '' ?>par <?= app_escape((string) $entry['actor']) ?></p>
                </div>
                <time datetime="<?= app_escape(str_replace(' ', 'T', (string) $entry['created_at'])) ?>"><?= app_escape((new DateTimeImmutable((string) $entry['created_at']))->format('d/m · H:i')) ?></time>
            </article>
        <?php endforeach; ?>
        <?php if ($audit === []): ?><div class="admin-empty compact"><span>≡</span><strong>Le journal est prêt</strong><p>La première action apparaîtra ici.</p></div><?php endif; ?>
    </div>
</section>

<?php admin_render_shell_end(); ?>

