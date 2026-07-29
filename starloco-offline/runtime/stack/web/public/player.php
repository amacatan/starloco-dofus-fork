<?php
declare(strict_types=1);

require_once __DIR__ . '/app/players.php';

app_security_headers();
$config = AppConfig::fromEnvironment();
$rawPlayerId = is_scalar($_GET['id'] ?? null) ? (string) $_GET['id'] : '';
$playerId = ctype_digit($rawPlayerId)
    && strlen($rawPlayerId) <= 10
    && (int) $rawPlayerId > 0
    && (int) $rawPlayerId <= 2147483647
        ? (int) $rawPlayerId
        : 0;
$player = null;
$profileError = null;

if ($playerId > 0) {
    try {
        $player = players_get_profile(app_pdo(), $playerId);
    } catch (Throwable $exception) {
        error_log('Public player profile error: ' . $exception->getMessage());
        $profileError = 'Le profil se synchronise avec le monde. Réessayez dans quelques instants.';
    }
}

if ($playerId === 0 || ($profileError === null && $player === null)) {
    http_response_code(404);
}

$pageTitle = $player === null
    ? 'Aventurier introuvable'
    : (string) $player['name'] . ' — ' . (string) $player['className'] . ' niveau ' . (int) $player['level'];
$pageDescription = $player === null
    ? 'Ce profil public n’existe pas ou n’est pas accessible.'
    : 'Profil public de ' . (string) $player['name'] . ', ' . (string) $player['className']
        . ' niveau ' . (int) $player['level'] . ' sur ' . $config->serverName . '.';
?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="<?= app_escape($pageDescription) ?>">
    <title><?= app_escape($pageTitle) ?> — <?= app_escape($config->serverName) ?></title>
    <link rel="stylesheet" href="/assets/app.css">
    <link rel="stylesheet" href="/assets/players.css">
</head>
<body class="player-profile-page<?= $player !== null ? ' class-theme-' . (int) $player['classId'] : '' ?>">
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

<main id="main-content" class="profile-shell">
    <nav class="breadcrumbs" aria-label="Fil d’Ariane">
        <a href="/players"><span aria-hidden="true">←</span> Tous les aventuriers</a>
        <?php if ($player !== null): ?>
            <span aria-hidden="true">/</span><span aria-current="page"><?= app_escape((string) $player['name']) ?></span>
        <?php endif; ?>
    </nav>

    <?php if ($profileError !== null): ?>
        <section class="profile-missing" role="status">
            <span class="missing-rune" aria-hidden="true">◌</span>
            <p class="players-eyebrow">Connexion au monde</p>
            <h1>Le parchemin tarde à s’ouvrir.</h1>
            <p><?= app_escape($profileError) ?></p>
            <a class="button-link" href="/player?id=<?= $playerId ?>">Réessayer</a>
        </section>
    <?php elseif ($player === null): ?>
        <section class="profile-missing">
            <span class="missing-rune" aria-hidden="true">?</span>
            <p class="players-eyebrow">Profil introuvable</p>
            <h1>Cet aventurier a quitté la carte.</h1>
            <p>Le personnage recherché n’existe pas, a été supprimé ou ne possède pas de profil public.</p>
            <a class="button-link" href="/players">Retourner à l’annuaire</a>
        </section>
    <?php else: ?>
        <section class="profile-hero" aria-labelledby="profile-name">
            <span class="profile-hero-grid" aria-hidden="true"></span>
            <div class="profile-avatar">
                <span class="profile-avatar-halo" aria-hidden="true"></span>
                <span class="profile-avatar-glyph" aria-hidden="true"><?= app_escape((string) $player['classGlyph']) ?></span>
                <span class="profile-level-medallion"><small>NIV.</small><?= (int) $player['level'] ?></span>
            </div>
            <div class="profile-hero-copy">
                <div class="profile-status-row">
                    <span class="presence <?= $player['online'] ? 'is-online' : 'is-offline' ?>">
                        <i aria-hidden="true"></i><?= $player['online'] ? 'En ligne maintenant' : 'Hors ligne' ?>
                    </span>
                    <span class="profile-id">Aventurier #<?= (int) $player['id'] ?></span>
                </div>
                <h1 id="profile-name"><?= app_escape((string) $player['name']) ?></h1>
                <p class="profile-subtitle">
                    <?= app_escape((string) $player['className']) ?>
                    <span aria-hidden="true">·</span>
                    <?= app_escape((string) $player['alignment']['glyph']) ?>
                    <?= app_escape((string) $player['alignment']['name']) ?>
                    <?php if ($player['guild'] !== null): ?>
                        <span aria-hidden="true">·</span>
                        <span class="guild-highlight">⚑ <?= app_escape((string) $player['guild']['name']) ?></span>
                    <?php endif; ?>
                </p>
                <div class="hero-progress">
                    <div>
                        <span>Progression vers le niveau ultime</span>
                        <strong><?= min(100, round(((int) $player['level'] / 200) * 100)) ?>%</strong>
                    </div>
                    <progress max="200" value="<?= min(200, (int) $player['level']) ?>">Niveau <?= (int) $player['level'] ?> sur 200</progress>
                </div>
            </div>
            <dl class="profile-hero-stats">
                <div><dt>Expérience</dt><dd><?= players_public_number((int) $player['xp']) ?></dd></div>
                <div><dt>Honneur</dt><dd><?= players_public_number((int) $player['honor']) ?></dd></div>
                <div><dt>Quêtes</dt><dd><?= players_public_number((int) $player['quests']['completed']) ?></dd></div>
            </dl>
        </section>

        <div class="profile-layout">
            <div class="profile-main-column">
                <section class="profile-panel equipment-panel" aria-labelledby="equipment-title">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Apparence en jeu</p>
                            <h2 id="equipment-title">Équipement porté</h2>
                        </div>
                        <span class="panel-chip"><?= count(array_filter($player['equipment'], static fn(array $slot): bool => $slot['item'] !== null)) ?>/17 emplacements</span>
                    </div>
                    <div class="equipment-grid">
                        <?php foreach ($player['equipment'] as $slot): ?>
                            <?php $item = $slot['item']; ?>
                            <article class="equipment-slot<?= $item === null ? ' is-empty' : ' has-item' ?>"
                                     aria-label="<?= app_escape((string) $slot['label']) ?><?= $item === null ? ', vide' : ', ' . app_escape((string) $item['name']) ?>">
                                <span class="slot-glyph" aria-hidden="true"><?= app_escape((string) $slot['glyph']) ?></span>
                                <div>
                                    <small><?= app_escape((string) $slot['label']) ?></small>
                                    <?php if ($item === null): ?>
                                        <strong>Emplacement vide</strong>
                                    <?php else: ?>
                                        <strong><?= app_escape((string) $item['name']) ?></strong>
                                        <span>Niveau <?= (int) $item['level'] ?><?= (int) $item['quantity'] > 1 ? ' · ×' . (int) $item['quantity'] : '' ?></span>
                                    <?php endif; ?>
                                </div>
                            </article>
                        <?php endforeach; ?>
                    </div>
                </section>

                <section class="profile-panel" aria-labelledby="characteristics-title">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Puissance naturelle</p>
                            <h2 id="characteristics-title">Caractéristiques de base</h2>
                        </div>
                        <span class="panel-chip"><?= (int) $player['healthPercent'] ?>% de vitalité actuelle</span>
                    </div>
                    <dl class="characteristic-grid">
                        <?php foreach ($player['characteristics'] as $characteristic): ?>
                            <div class="characteristic <?= app_escape((string) $characteristic['tone']) ?>">
                                <dt><span aria-hidden="true"><?= app_escape((string) $characteristic['glyph']) ?></span><?= app_escape((string) $characteristic['name']) ?></dt>
                                <dd><?= players_public_number((int) $characteristic['value']) ?></dd>
                            </div>
                        <?php endforeach; ?>
                    </dl>
                </section>

                <section class="profile-panel quest-panel" aria-labelledby="quests-title">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Carnet d’aventure</p>
                            <h2 id="quests-title">Quêtes accomplies</h2>
                        </div>
                        <?php if ($player['quests']['available']): ?>
                            <span class="quest-count"><?= players_public_number((int) $player['quests']['completed']) ?></span>
                        <?php endif; ?>
                    </div>
                    <?php if (!$player['quests']['available']): ?>
                        <div class="panel-empty"><span aria-hidden="true">◌</span><p>La progression des quêtes n’est pas encore disponible.</p></div>
                    <?php elseif ($player['quests']['recent'] === []): ?>
                        <div class="panel-empty"><span aria-hidden="true">□</span><p>Aucune quête terminée n’a encore été consignée.</p></div>
                    <?php else: ?>
                        <ol class="quest-list">
                            <?php foreach ($player['quests']['recent'] as $quest): ?>
                                <li>
                                    <span class="quest-check" aria-hidden="true">✓</span>
                                    <div>
                                        <strong><?= app_escape((string) $quest['name']) ?></strong>
                                        <small>
                                            Quête #<?= (int) $quest['id'] ?>
                                            <?php if ($quest['completedAt'] !== null): ?> · consignée le <?= app_escape(players_public_date((string) $quest['completedAt'])) ?><?php endif; ?>
                                        </small>
                                    </div>
                                </li>
                            <?php endforeach; ?>
                        </ol>
                    <?php endif; ?>
                </section>

                <section class="profile-panel activity-panel" aria-labelledby="activity-title">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Chronique publique</p>
                            <h2 id="activity-title">Derniers exploits</h2>
                        </div>
                        <a class="panel-link" href="/">Voir tout le fil <span aria-hidden="true">→</span></a>
                    </div>
                    <?php if ($player['activity'] === []): ?>
                        <div class="panel-empty"><span aria-hidden="true">✦</span><p>Les prochains exploits de cet aventurier apparaîtront ici.</p></div>
                    <?php else: ?>
                        <ol class="profile-timeline">
                            <?php foreach ($player['activity'] as $event): ?>
                                <li class="importance-<?= (int) $event['importance'] ?>">
                                    <span class="timeline-icon" aria-hidden="true"><?= app_escape((string) $event['icon']) ?></span>
                                    <div>
                                        <span class="timeline-meta"><?= app_escape((string) $event['label']) ?> · <?= app_escape(players_public_date((string) $event['happenedAt'])) ?></span>
                                        <strong><?= app_escape((string) $event['title']) ?></strong>
                                        <?php if ((string) $event['detail'] !== ''): ?><p><?= app_escape((string) $event['detail']) ?></p><?php endif; ?>
                                    </div>
                                </li>
                            <?php endforeach; ?>
                        </ol>
                    <?php endif; ?>
                </section>
            </div>

            <aside class="profile-sidebar" aria-label="Résumé du personnage">
                <section class="profile-panel identity-panel">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Identité</p>
                            <h2>Parchemin du héros</h2>
                        </div>
                    </div>
                    <dl class="identity-list">
                        <div><dt>Classe</dt><dd><span class="identity-rune" aria-hidden="true"><?= app_escape((string) $player['classGlyph']) ?></span><?= app_escape((string) $player['className']) ?></dd></div>
                        <div><dt>Niveau</dt><dd><?= (int) $player['level'] ?> / 200</dd></div>
                        <div><dt>Alignement</dt><dd class="<?= app_escape((string) $player['alignment']['tone']) ?>"><?= app_escape((string) $player['alignment']['glyph']) ?> <?= app_escape((string) $player['alignment']['name']) ?></dd></div>
                        <?php if ((int) $player['alignmentLevel'] > 0): ?><div><dt>Grade d’alignement</dt><dd><?= (int) $player['alignmentLevel'] ?></dd></div><?php endif; ?>
                        <div><dt>Sexe du personnage</dt><dd><?= (int) $player['sex'] === 0 ? 'Masculin' : 'Féminin' ?></dd></div>
                    </dl>
                </section>

                <?php if ($player['guild'] !== null): ?>
                    <section class="profile-panel guild-panel">
                        <span class="guild-emblem" aria-hidden="true">⚑</span>
                        <p class="panel-overline">Guilde</p>
                        <h2><?= app_escape((string) $player['guild']['name']) ?></h2>
                        <p><?= app_escape((string) $player['guild']['rank']) ?> · Guilde niveau <?= (int) $player['guild']['level'] ?></p>
                        <dl>
                            <div><dt>Expérience donnée</dt><dd><?= players_public_number((int) $player['guild']['xpGiven']) ?></dd></div>
                            <div><dt>Partage actuel</dt><dd><?= (int) $player['guild']['xpPercent'] ?>%</dd></div>
                        </dl>
                    </section>
                <?php endif; ?>

                <section class="profile-panel combat-panel">
                    <div class="profile-panel-heading">
                        <div>
                            <p class="panel-overline">Combat</p>
                            <h2>Tableau de chasse</h2>
                        </div>
                    </div>
                    <div class="combat-score">
                        <div><strong><?= players_public_number((int) $player['combat']['kills']) ?></strong><span>victoires</span></div>
                        <span class="score-divider" aria-hidden="true">/</span>
                        <div><strong><?= players_public_number((int) $player['combat']['deaths']) ?></strong><span>défaites</span></div>
                    </div>
                    <div class="ratio-line"><span>Ratio de combat</span><strong><?= number_format((float) $player['combat']['ratio'], 2, ',', ' ') ?></strong></div>
                </section>

                <section class="profile-privacy">
                    <span aria-hidden="true">◈</span>
                    <div>
                        <strong>Profil public protégé</strong>
                        <p>Aucun identifiant de compte, e-mail, kama ou emplacement n’est affiché.</p>
                    </div>
                </section>
            </aside>
        </div>
    <?php endif; ?>
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
