<?php
declare(strict_types=1);
require_once __DIR__ . '/app/social.php';

app_security_headers();
$config = AppConfig::fromEnvironment();
$allowedFilters = ['all', 'progression', 'loot', 'guild', 'community'];
$filter = is_scalar($_GET['filter'] ?? null) ? (string) $_GET['filter'] : 'all';
if (!in_array($filter, $allowedFilters, true)) {
    $filter = 'all';
}

$events = [];
$dashboard = [
    'stats' => ['characters' => 0, 'online' => 0, 'maxLevel' => 0, 'level200' => 0, 'guilds' => 0, 'eventsToday' => 0],
    'worker' => ['initialized' => false, 'online' => false],
    'leaderboard' => [],
];
$feedError = null;
try {
    $pdo = app_pdo();
    $events = social_get_events($pdo, $filter, 25);
    $dashboard = social_get_dashboard($pdo);
} catch (Throwable $exception) {
    error_log('Social feed error: ' . $exception->getMessage());
    $feedError = 'Le fil communautaire démarre. Rechargez la page dans quelques instants.';
}

$latestId = $events === [] ? 0 : max(array_column($events, 'id'));
$oldestId = $events === [] ? 0 : min(array_column($events, 'id'));

/** @param array<string,mixed> $event */
function render_event(array $event): void
{
    $importance = max(1, min(3, (int) ($event['importance'] ?? 1)));
    $date = new DateTimeImmutable((string) $event['happenedAt']);
    ?>
    <article class="event-card importance-<?= $importance ?>" data-event-id="<?= (int) $event['id'] ?>" data-event-type="<?= app_escape((string) $event['type']) ?>">
        <?php if ($event['playerId'] !== null): ?>
            <a class="event-avatar" href="/player?id=<?= (int) $event['playerId'] ?>" aria-label="Profil de <?= app_escape((string) $event['playerName']) ?>"><?= app_escape((string) $event['classGlyph']) ?></a>
        <?php else: ?>
            <div class="event-avatar" aria-hidden="true"><?= app_escape((string) $event['icon']) ?></div>
        <?php endif; ?>
        <div class="event-content">
            <div class="event-meta">
                <span class="event-kind"><span aria-hidden="true"><?= app_escape((string) $event['icon']) ?></span> <?= app_escape((string) $event['label']) ?></span>
                <time datetime="<?= app_escape((string) $event['happenedAt']) ?>" title="<?= app_escape($date->format('d/m/Y à H:i')) ?>"><?= app_escape($date->format('d/m/Y · H:i')) ?></time>
            </div>
            <h2>
                <?php if ($event['playerId'] !== null): ?><a class="event-title-link" href="/player?id=<?= (int) $event['playerId'] ?>"><?php endif; ?>
                <?= app_escape((string) $event['title']) ?>
                <?php if ($event['playerId'] !== null): ?></a><?php endif; ?>
            </h2>
            <?php if ((string) $event['detail'] !== ''): ?>
                <p><?= app_escape((string) $event['detail']) ?></p>
            <?php endif; ?>
            <?php if ($event['playerName'] !== null): ?>
                <div class="event-tags">
                    <span><?= app_escape((string) $event['className']) ?></span>
                    <?php if ($event['guildName'] !== null): ?><span><?= app_escape((string) $event['guildName']) ?></span><?php endif; ?>
                    <?php if ($event['itemLevel'] !== null && (int) $event['itemLevel'] > 0): ?><span>Niv. <?= (int) $event['itemLevel'] ?></span><?php endif; ?>
                    <a href="/player?id=<?= (int) $event['playerId'] ?>">Voir le profil <span aria-hidden="true">→</span></a>
                </div>
            <?php endif; ?>
        </div>
    </article>
    <?php
}

$filters = [
    'all' => 'Tout',
    'progression' => 'Progression',
    'loot' => 'Butins',
    'guild' => 'Guildes',
    'community' => 'Communauté',
];
?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Le fil communautaire en direct du serveur <?= app_escape($config->serverName) ?>.">
    <title>Fil des aventuriers — <?= app_escape($config->serverName) ?></title>
    <link rel="stylesheet" href="/assets/app.css">
    <script src="/assets/app.js" defer></script>
</head>
<body class="feed-page" data-current-filter="<?= app_escape($filter) ?>">
<header class="site-header">
    <a class="brand" href="/" aria-label="Accueil <?= app_escape($config->serverName) ?>">
        <span class="brand-mark">S</span>
        <span><strong><?= app_escape($config->serverName) ?></strong><small>Retro 1.41.9</small></span>
    </a>
    <nav class="top-nav" aria-label="Navigation principale">
        <a class="active" href="/">Fil des aventuriers</a>
        <a href="/players">Annuaire</a>
        <a class="button-link compact" href="/register.php">Créer un compte</a>
    </nav>
</header>

<main class="community-shell">
    <section class="feed-hero">
        <div>
            <p class="eyebrow"><span class="live-dot" aria-hidden="true"></span> Communauté en direct</p>
            <h1>Le fil des aventuriers</h1>
            <p>Niveaux, objets remarquables, quêtes et vie des guildes — les exploits du serveur réunis au même endroit.</p>
        </div>
        <a class="button-link hero-action" href="/register.php">Commencer l’aventure <span aria-hidden="true">→</span></a>
    </section>

    <section class="metric-strip" aria-label="État du serveur">
        <div class="metric"><span class="metric-value" data-stat="online"><?= (int) $dashboard['stats']['online'] ?></span><span>en ligne</span></div>
        <div class="metric"><span class="metric-value" data-stat="characters"><?= (int) $dashboard['stats']['characters'] ?></span><span>personnages</span></div>
        <div class="metric"><span class="metric-value" data-stat="maxLevel"><?= (int) $dashboard['stats']['maxLevel'] ?></span><span>niveau max</span></div>
        <div class="metric"><span class="metric-value" data-stat="guilds"><?= (int) $dashboard['stats']['guilds'] ?></span><span>guildes</span></div>
    </section>

    <div class="community-layout">
        <section class="feed-column" aria-labelledby="feed-heading">
            <div class="feed-toolbar">
                <div>
                    <h2 id="feed-heading">Activité récente</h2>
                    <p id="feed-status" aria-live="polite">
                        <?php if (($dashboard['worker']['online'] ?? false) === true): ?>Mis à jour automatiquement<?php else: ?>Synchronisation en cours<?php endif; ?>
                    </p>
                </div>
                <nav class="filter-tabs" aria-label="Filtrer le fil">
                    <?php foreach ($filters as $key => $label): ?>
                        <a href="/?filter=<?= app_escape($key) ?>"<?= $key === $filter ? ' class="active" aria-current="page"' : '' ?>><?= app_escape($label) ?></a>
                    <?php endforeach; ?>
                </nav>
            </div>

            <?php if ($feedError !== null): ?>
                <div class="notice warning" role="status"><?= app_escape($feedError) ?></div>
            <?php endif; ?>

            <div id="feed-list" class="feed-list" data-latest-id="<?= $latestId ?>" data-oldest-id="<?= $oldestId ?>">
                <?php foreach ($events as $event) { render_event($event); } ?>
            </div>

            <div id="empty-feed" class="empty-feed"<?= $events !== [] ? ' hidden' : '' ?>>
                <span aria-hidden="true">✦</span>
                <h2>Les aventures commencent ici</h2>
                <p>Le premier niveau gagné, objet remarquable ou personnage créé apparaîtra automatiquement.</p>
            </div>

            <?php if (count($events) >= 25): ?>
                <button id="load-more" class="secondary-button" type="button">Afficher plus d’activités</button>
            <?php endif; ?>
        </section>

        <aside class="sidebar" aria-label="Classements et informations">
            <section class="side-panel server-card">
                <div class="side-heading">
                    <h2>Serveur</h2>
                    <span class="status-pill <?= ($dashboard['worker']['online'] ?? false) ? 'online' : 'syncing' ?>">
                        <?= ($dashboard['worker']['online'] ?? false) ? 'En direct' : 'Synchronisation' ?>
                    </span>
                </div>
                <dl class="server-details">
                    <div><dt>Activités aujourd’hui</dt><dd data-stat="eventsToday"><?= (int) $dashboard['stats']['eventsToday'] ?></dd></div>
                    <div><dt>Niveaux 200</dt><dd data-stat="level200"><?= (int) $dashboard['stats']['level200'] ?></dd></div>
                    <div><dt>Version</dt><dd>1.41.9</dd></div>
                </dl>
            </section>

            <section class="side-panel">
                <div class="side-heading"><h2>Les plus avancés</h2></div>
                <ol class="leaderboard" id="leaderboard">
                    <?php foreach ($dashboard['leaderboard'] as $leader): ?>
                        <li>
                            <span class="rank"><?= (int) $leader['position'] ?></span>
                            <span class="mini-avatar" aria-hidden="true"><?= app_escape((string) $leader['classGlyph']) ?></span>
                            <a class="leader-name" href="/player?id=<?= (int) $leader['id'] ?>"><strong><?= app_escape((string) $leader['name']) ?></strong><small><?= app_escape((string) $leader['className']) ?></small></a>
                            <span class="leader-level">Niv. <?= (int) $leader['level'] ?></span>
                            <?php if ($leader['online']): ?><span class="online-indicator" title="En ligne"><span class="sr-only">En ligne</span></span><?php endif; ?>
                        </li>
                    <?php endforeach; ?>
                </ol>
            </section>

            <section class="side-panel privacy-note">
                <span aria-hidden="true">◉</span>
                <div><h2>Vie privée préservée</h2><p>Le fil n’affiche que les noms de personnages et les données publiques du jeu. Aucun compte, e-mail ou IP n’est exposé.</p></div>
            </section>
        </aside>
    </div>
</main>

<footer class="site-footer">
    <span><?= app_escape($config->serverName) ?></span>
    <span>Fil actualisé automatiquement toutes les quelques secondes.</span>
</footer>
</body>
</html>
