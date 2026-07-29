<?php
declare(strict_types=1);

require_once __DIR__ . '/app/players.php';

app_security_headers();
$config = AppConfig::fromEnvironment();
$filters = players_normalize_filters($_GET);
$directory = ['players' => [], 'total' => 0];
$stats = ['characters' => 0, 'online' => 0, 'maxLevel' => 0, 'level200' => 0, 'guilds' => 0];
$directoryError = null;

try {
    $pdo = app_pdo();
    $directory = players_get_directory($pdo, $filters);
    $stats = players_get_public_stats($pdo);
} catch (Throwable $exception) {
    error_log('Public player directory error: ' . $exception->getMessage());
    $directoryError = 'L’annuaire se synchronise avec le monde. Réessayez dans quelques instants.';
}

$classes = [];
for ($classId = 1; $classId <= 12; $classId++) {
    $classes[$classId] = players_class_meta($classId)['name'];
}
$filtersActive = $filters['q'] !== ''
    || $filters['class'] !== 'all'
    || $filters['status'] !== 'all'
    || $filters['sort'] !== 'level';
$visibleCount = count($directory['players']);
?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Explorez les personnages, guildes et exploits publics du serveur <?= app_escape($config->serverName) ?>.">
    <title>Annuaire des aventuriers — <?= app_escape($config->serverName) ?></title>
    <link rel="stylesheet" href="/assets/app.css">
    <link rel="stylesheet" href="/assets/players.css">
</head>
<body class="players-page">
<a class="skip-link" href="#main-content">Aller au contenu</a>

<header class="site-header players-header">
    <a class="brand" href="/" aria-label="Accueil <?= app_escape($config->serverName) ?>">
        <span class="brand-mark">S</span>
        <span><strong><?= app_escape($config->serverName) ?></strong><small>Retro 1.41.9</small></span>
    </a>
    <nav class="top-nav" aria-label="Navigation principale">
        <a href="/">Fil des aventuriers</a>
        <a class="active" href="/players" aria-current="page">Annuaire</a>
        <a class="button-link compact" href="/register.php">Créer un compte</a>
    </nav>
</header>

<main id="main-content" class="players-shell">
    <section class="directory-hero" aria-labelledby="directory-title">
        <div class="directory-hero-copy">
            <p class="players-eyebrow"><span aria-hidden="true">✦</span> Hall des aventuriers</p>
            <h1 id="directory-title">Chaque légende<br><span>a un nom.</span></h1>
            <p>Parcourez les héros de <?= app_escape($config->serverName) ?>, découvrez leurs parcours et trouvez vos prochains compagnons d’aventure.</p>
        </div>
        <div class="world-orbit" aria-hidden="true">
            <span class="orbit-ring orbit-ring-one"></span>
            <span class="orbit-ring orbit-ring-two"></span>
            <span class="orbit-core">S</span>
            <span class="orbit-star orbit-star-one">✦</span>
            <span class="orbit-star orbit-star-two">◆</span>
            <span class="orbit-star orbit-star-three">·</span>
        </div>
    </section>

    <section class="directory-metrics" aria-label="Chiffres de la communauté">
        <article>
            <span class="metric-symbol online-symbol" aria-hidden="true"></span>
            <div><strong><?= players_public_number($stats['online']) ?></strong><span>en ligne</span></div>
        </article>
        <article>
            <span class="metric-symbol" aria-hidden="true">♙</span>
            <div><strong><?= players_public_number($stats['characters']) ?></strong><span>personnages</span></div>
        </article>
        <article>
            <span class="metric-symbol" aria-hidden="true">⚑</span>
            <div><strong><?= players_public_number($stats['guilds']) ?></strong><span>guildes</span></div>
        </article>
        <article>
            <span class="metric-symbol" aria-hidden="true">↑</span>
            <div><strong><?= players_public_number($stats['maxLevel']) ?></strong><span>niveau record</span></div>
        </article>
    </section>

    <section class="directory-browser" aria-labelledby="browser-title">
        <div class="section-kicker">
            <div>
                <p class="players-eyebrow">Explorer le monde</p>
                <h2 id="browser-title">Trouver un aventurier</h2>
            </div>
            <p class="result-count" aria-live="polite">
                <strong><?= players_public_number($directory['total']) ?></strong>
                <?= $directory['total'] === 1 ? 'résultat' : 'résultats' ?>
            </p>
        </div>

        <form class="player-filters" action="/players" method="get" role="search">
            <div class="search-field">
                <label for="player-search">Nom du personnage</label>
                <span class="search-icon" aria-hidden="true"></span>
                <input id="player-search" name="q" type="search" maxlength="30"
                       value="<?= app_escape($filters['q']) ?>" placeholder="Ex. Amakna..." autocomplete="off">
            </div>
            <div class="select-field">
                <label for="class-filter">Classe</label>
                <select id="class-filter" name="class">
                    <option value="all">Toutes les classes</option>
                    <?php foreach ($classes as $classId => $className): ?>
                        <option value="<?= $classId ?>"<?= $filters['class'] === (string) $classId ? ' selected' : '' ?>>
                            <?= app_escape($className) ?>
                        </option>
                    <?php endforeach; ?>
                </select>
            </div>
            <div class="select-field">
                <label for="status-filter">Présence</label>
                <select id="status-filter" name="status">
                    <option value="all">Tous les statuts</option>
                    <option value="online"<?= $filters['status'] === 'online' ? ' selected' : '' ?>>En ligne</option>
                    <option value="offline"<?= $filters['status'] === 'offline' ? ' selected' : '' ?>>Hors ligne</option>
                </select>
            </div>
            <div class="select-field">
                <label for="sort-filter">Trier par</label>
                <select id="sort-filter" name="sort">
                    <option value="level"<?= $filters['sort'] === 'level' ? ' selected' : '' ?>>Niveau</option>
                    <option value="name"<?= $filters['sort'] === 'name' ? ' selected' : '' ?>>Nom</option>
                    <option value="pvp"<?= $filters['sort'] === 'pvp' ? ' selected' : '' ?>>Honneur</option>
                    <option value="combat"<?= $filters['sort'] === 'combat' ? ' selected' : '' ?>>Victoires</option>
                    <option value="recent"<?= $filters['sort'] === 'recent' ? ' selected' : '' ?>>Plus récents</option>
                </select>
            </div>
            <button class="filter-submit" type="submit"><span aria-hidden="true">⌕</span> Rechercher</button>
            <?php if ($filtersActive): ?>
                <a class="filter-reset" href="/players">Réinitialiser</a>
            <?php endif; ?>
        </form>

        <?php if ($directoryError !== null): ?>
            <div class="player-notice" role="status">
                <span aria-hidden="true">◌</span>
                <p><?= app_escape($directoryError) ?></p>
            </div>
        <?php elseif ($directory['players'] === []): ?>
            <div class="directory-empty">
                <span class="empty-glyph" aria-hidden="true">⌕</span>
                <h3>Aucun aventurier à l’horizon</h3>
                <p>Essayez un autre nom ou élargissez vos filtres pour reprendre l’exploration.</p>
                <?php if ($filtersActive): ?><a class="button-link" href="/players">Voir tout l’annuaire</a><?php endif; ?>
            </div>
        <?php else: ?>
            <div class="player-card-grid" aria-label="<?= $visibleCount ?> personnages affichés">
                <?php foreach ($directory['players'] as $player): ?>
                    <a class="player-card class-<?= (int) $player['classId'] ?>" href="/player?id=<?= (int) $player['id'] ?>"
                       aria-label="Voir le profil de <?= app_escape((string) $player['name']) ?>">
                        <span class="card-glow" aria-hidden="true"></span>
                        <div class="player-card-top">
                            <div class="class-avatar">
                                <span aria-hidden="true"><?= app_escape((string) $player['classGlyph']) ?></span>
                                <small><?= app_escape((string) $player['className']) ?></small>
                            </div>
                            <span class="presence <?= $player['online'] ? 'is-online' : 'is-offline' ?>">
                                <i aria-hidden="true"></i><?= $player['online'] ? 'En ligne' : 'Hors ligne' ?>
                            </span>
                        </div>
                        <div class="player-card-identity">
                            <h3><?= app_escape((string) $player['name']) ?></h3>
                            <?php if ($player['guildName'] !== null): ?>
                                <p><span aria-hidden="true">⚑</span> <?= app_escape((string) $player['guildName']) ?></p>
                            <?php else: ?>
                                <p class="without-guild">Aucune guilde</p>
                            <?php endif; ?>
                        </div>
                        <div class="level-line">
                            <span>Niveau <strong><?= (int) $player['level'] ?></strong></span>
                            <progress max="200" value="<?= min(200, (int) $player['level']) ?>">Niveau <?= (int) $player['level'] ?> sur 200</progress>
                        </div>
                        <dl class="card-statline">
                            <div><dt>Honneur</dt><dd><?= players_public_number((int) $player['honor']) ?></dd></div>
                            <div><dt>Victoires</dt><dd><?= players_public_number((int) $player['totalKills']) ?></dd></div>
                            <div class="alignment-mini <?= app_escape((string) $player['alignment']['tone']) ?>">
                                <dt>Alignement</dt>
                                <dd><?= app_escape((string) $player['alignment']['glyph']) ?> <?= app_escape((string) $player['alignment']['name']) ?></dd>
                            </div>
                        </dl>
                        <span class="profile-callout">Voir le profil <span aria-hidden="true">→</span></span>
                    </a>
                <?php endforeach; ?>
            </div>
            <?php if ($directory['total'] > $visibleCount): ?>
                <p class="result-limit-note">Les <?= $visibleCount ?> premiers résultats sont affichés. Affinez la recherche pour trouver un personnage précis.</p>
            <?php endif; ?>
        <?php endif; ?>
    </section>

    <aside class="public-privacy-card" aria-label="Protection de la vie privée">
        <span class="privacy-shield" aria-hidden="true">◈</span>
        <div>
            <strong>La vitrine des exploits, jamais des comptes.</strong>
            <p>Seules les informations publiques du personnage sont visibles. Identifiants, e-mails, kamas et localisation restent strictement privés.</p>
        </div>
    </aside>
</main>

<footer class="site-footer players-footer">
    <span><?= app_escape($config->serverName) ?> · Dofus Retro 1.41.9</span>
    <nav aria-label="Navigation de pied de page">
        <a href="/">Activité</a>
        <a href="/players">Aventuriers</a>
        <a href="/register.php">Nous rejoindre</a>
    </nav>
</footer>
</body>
</html>
