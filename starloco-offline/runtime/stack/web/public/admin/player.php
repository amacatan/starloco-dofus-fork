<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/admin-layout.php';

app_security_headers();
admin_require_auth();
admin_require_capability('players.view');

$playerId = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
if ($playerId === false || $playerId === null) {
    http_response_code(404);
    admin_render_shell_start('Personnage introuvable', 'L’identifiant demandé est invalide.', 'characters');
    ?><div class="admin-empty standalone"><span>?</span><strong>Personnage introuvable</strong><a class="primary-action" href="/admin/">Retour à la console</a></div><?php
    admin_render_shell_end();
    exit;
}

$console = admin_console();
$player = $console->player((int) $playerId);
if ($player === null) {
    http_response_code(404);
    admin_render_shell_start('Personnage introuvable', 'Ce personnage n’existe plus.', 'characters');
    ?><div class="admin-empty standalone"><span>?</span><strong>Personnage introuvable</strong><a class="primary-action" href="/admin/">Retour à la console</a></div><?php
    admin_render_shell_end();
    exit;
}

$inventory = $console->inventory($player);
$audit = $console->recentAudit(12, (int) $playerId);
$csrf = admin_csrf_token();
$editable = (bool) $player['editable'];

admin_render_shell_start((string) $player['name'], 'Édition contrôlée du personnage et de son inventaire.', 'characters');
admin_render_flash(admin_take_flash());
?>

<nav class="admin-breadcrumb" aria-label="Fil d’Ariane">
    <a href="/admin/">Vue d’ensemble</a><span>›</span><span><?= app_escape((string) $player['name']) ?></span>
</nav>

<section class="character-command-card class-<?= (int) $player['class'] ?>">
    <div class="command-avatar">
        <span><?= app_escape((string) $player['classGlyph']) ?></span>
        <i class="<?= $player['online'] ? 'online' : 'offline' ?>"></i>
    </div>
    <div class="command-identity">
        <p><?= app_escape((string) $player['className']) ?> · Niveau <?= (int) $player['level'] ?></p>
        <h2><?= app_escape((string) $player['name']) ?></h2>
        <div class="command-tags">
            <span>Compte <?= app_escape((string) $player['account_name']) ?></span>
            <?php if ($player['guild_name']): ?><span>⚑ <?= app_escape((string) $player['guild_name']) ?></span><?php endif; ?>
            <span>ID <?= (int) $player['id'] ?></span>
        </div>
    </div>
    <div class="command-state">
        <span class="admin-status <?= $player['online'] ? 'online' : 'offline' ?>"><i></i><?= $player['online'] ? 'En ligne' : 'Prêt à éditer' ?></span>
        <a href="/player?id=<?= (int) $player['id'] ?>" target="_blank" rel="noopener">Voir la fiche publique ↗</a>
    </div>
    <div class="command-scan" aria-hidden="true"></div>
</section>

<?php if (!$editable): ?>
    <div class="offline-lock-notice">
        <span aria-hidden="true">⌁</span>
        <div><strong>Édition verrouillée</strong><p>Déconnectez le personnage puis rechargez cette page. Le serveur garde ses données en mémoire tant qu’il est en ligne.</p></div>
        <button type="button" data-reload-page>Recharger</button>
    </div>
<?php endif; ?>

<div class="editor-grid">
    <div class="editor-main">
        <section class="admin-panel edit-section">
            <div class="admin-panel-heading">
                <div><p class="panel-overline">Économie</p><h2>Portefeuille</h2><p>Solde maximum conforme au serveur : 1 milliard.</p></div>
                <strong class="wallet-total"><?= admin_format_number((int) $player['kamas']) ?> <small>K</small></strong>
            </div>
            <fieldset<?= $editable ? '' : ' disabled' ?>>
                <div class="wallet-actions">
                    <form method="post" action="/admin/action" class="inline-admin-form">
                        <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                        <input type="hidden" name="action" value="set_kamas">
                        <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                        <label for="kamas">Définir le solde</label>
                        <div class="compound-input"><input id="kamas" name="kamas" inputmode="numeric" pattern="[0-9]+" value="<?= (int) $player['kamas'] ?>" required><span>K</span><button type="submit">Appliquer</button></div>
                    </form>
                    <form method="post" action="/admin/action" class="inline-admin-form">
                        <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                        <input type="hidden" name="action" value="adjust_kamas">
                        <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                        <label for="delta">Ajouter ou retirer</label>
                        <div class="compound-input"><input id="delta" name="delta" inputmode="numeric" pattern="-?[0-9]+" placeholder="+100000" required><span>K</span><button type="submit">Ajuster</button></div>
                        <div class="quick-values" aria-label="Montants rapides">
                            <button type="button" data-fill-value="10000" data-fill-target="delta">+10k</button>
                            <button type="button" data-fill-value="100000" data-fill-target="delta">+100k</button>
                            <button type="button" data-fill-value="1000000" data-fill-target="delta">+1M</button>
                            <button type="button" data-fill-value="-100000" data-fill-target="delta">−100k</button>
                        </div>
                    </form>
                </div>
            </fieldset>
        </section>

        <section class="admin-panel edit-section">
            <div class="admin-panel-heading">
                <div><p class="panel-overline">Atelier d’objets</p><h2>Ajouter un objet</h2><p>Recherche instantanée dans les templates compatibles.</p></div>
                <span class="safe-badge">Création sûre</span>
            </div>
            <fieldset<?= $editable ? '' : ' disabled' ?>>
                <form method="post" action="/admin/action" class="item-giver" data-item-form>
                    <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                    <input type="hidden" name="action" value="give_item">
                    <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                    <input type="hidden" name="template_id" value="" data-template-id>
                    <div class="item-search-field">
                        <label for="item-search">Nom ou ID du template</label>
                        <div class="control-input"><span>⌕</span><input id="item-search" type="search" maxlength="60" autocomplete="off" placeholder="Ex. Coiffe du Bouftou…" data-item-search required></div>
                        <div class="item-search-results" data-item-results hidden></div>
                        <p class="selected-item" data-selected-item>Aucun objet sélectionné.</p>
                    </div>
                    <div class="item-options">
                        <div><label for="quantity">Quantité</label><input id="quantity" name="quantity" type="number" min="1" max="99999" value="1" required></div>
                        <div><label for="quality">Jet</label><select id="quality" name="quality"><option value="base">Jet de base</option><option value="perfect">Jet parfait</option></select></div>
                        <button type="submit" disabled data-give-submit>Ajouter à l’inventaire <span>＋</span></button>
                    </div>
                </form>
            </fieldset>
        </section>

        <section class="admin-panel edit-section inventory-section">
            <div class="admin-panel-heading">
                <div><p class="panel-overline">Inventaire</p><h2><?= count($inventory) ?> pile<?= count($inventory) > 1 ? 's' : '' ?> d’objets</h2><p>Les objets spéciaux restent protégés et doivent être gérés en jeu.</p></div>
                <span class="inventory-count"><?= array_sum(array_map(static fn(array $item): int => (int) $item['quantity'], $inventory)) ?> unités</span>
            </div>
            <div class="inventory-admin-list">
                <?php foreach ($inventory as $item):
                    $support = $item['type'] === null
                        ? ['supported' => false, 'reason' => 'Template absent']
                        : admin_item_support((int) $item['template_id'], (int) $item['type']);
                    ?>
                    <article class="inventory-admin-row<?= !$support['supported'] ? ' protected' : '' ?>">
                        <div class="item-rune"><span><?= mb_strtoupper(mb_substr((string) ($item['name'] ?? '?'), 0, 1)) ?></span></div>
                        <div class="item-admin-name">
                            <strong><?= app_escape((string) ($item['name'] ?? 'Template absent')) ?></strong>
                            <small>#<?= (int) $item['template_id'] ?> · <?= app_escape((string) $item['typeName']) ?> · Niv. <?= (int) ($item['level'] ?? 0) ?><?= (int) $item['position'] >= 0 ? ' · Équipé' : '' ?></small>
                        </div>
                        <span class="quantity-pill">× <?= admin_format_number((int) $item['quantity']) ?></span>
                        <?php if ($support['supported']): ?>
                            <form method="post" action="/admin/action" class="remove-item-form" data-confirm="Retirer cet objet de l’inventaire ?">
                                <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                                <input type="hidden" name="action" value="remove_item">
                                <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                                <input type="hidden" name="object_id" value="<?= (int) $item['id'] ?>">
                                <input name="quantity" type="number" min="1" max="<?= (int) $item['quantity'] ?>" value="<?= (int) $item['quantity'] ?>" aria-label="Quantité à retirer"<?= $editable ? '' : ' disabled' ?>>
                                <button class="danger-icon-button" type="submit" aria-label="Retirer <?= app_escape((string) ($item['name'] ?? 'objet')) ?>"<?= $editable ? '' : ' disabled' ?>>×</button>
                            </form>
                        <?php else: ?>
                            <span class="protected-chip" title="<?= app_escape((string) $support['reason']) ?>">Protégé</span>
                        <?php endif; ?>
                    </article>
                <?php endforeach; ?>
                <?php if ($inventory === []): ?><div class="admin-empty compact"><span>◇</span><strong>Inventaire vide</strong><p>Utilisez l’atelier pour offrir le premier objet.</p></div><?php endif; ?>
            </div>
        </section>
    </div>

    <aside class="editor-side">
        <section class="admin-panel edit-section">
            <div class="admin-panel-heading"><div><p class="panel-overline">Progression</p><h2>Caractéristiques</h2></div></div>
            <fieldset<?= $editable ? '' : ' disabled' ?>>
                <form method="post" action="/admin/action" class="stats-editor">
                    <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                    <input type="hidden" name="action" value="set_resources">
                    <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                    <?php
                    $statsFields = [
                        'vitalite' => ['Vitalité', '♥'],
                        'force' => ['Force', '◆'],
                        'sagesse' => ['Sagesse', '✦'],
                        'intelligence' => ['Intelligence', '●'],
                        'chance' => ['Chance', '◈'],
                        'agilite' => ['Agilité', '➤'],
                    ];
                    foreach ($statsFields as $field => [$label, $icon]): ?>
                        <label><span class="stat-label"><i><?= $icon ?></i><?= $label ?></span><input name="<?= $field ?>" type="number" min="0" max="1000000" value="<?= (int) $player[$field] ?>" required></label>
                    <?php endforeach; ?>
                    <div class="point-fields">
                        <label><span>Points de caractéristiques</span><input name="capital" type="number" min="0" max="1000000" value="<?= (int) $player['capital'] ?>" required></label>
                        <label><span>Points de sorts</span><input name="spellboost" type="number" min="0" max="1000000" value="<?= (int) $player['spellboost'] ?>" required></label>
                        <label><span>Énergie</span><input name="energy" type="number" min="0" max="10000" value="<?= (int) $player['energy'] ?>" required></label>
                    </div>
                    <button type="submit">Enregistrer les caractéristiques</button>
                </form>
            </fieldset>
        </section>

        <section class="admin-panel edit-section account-card">
            <div class="admin-panel-heading"><div><p class="panel-overline">Compte</p><h2>Modération</h2></div></div>
            <dl class="account-facts">
                <div><dt>Compte</dt><dd><?= app_escape((string) $player['account_name']) ?></dd></div>
                <div><dt>Pseudo</dt><dd><?= app_escape((string) $player['pseudo']) ?></dd></div>
                <div><dt>E-mail</dt><dd><?= app_escape((string) $player['email']) ?></dd></div>
                <div><dt>État</dt><dd><?= (int) $player['banned'] === 1 ? 'Banni' : 'Actif' ?></dd></div>
            </dl>
            <form method="post" action="/admin/action" data-confirm="<?= (int) $player['banned'] === 1 ? 'Débannir ce compte ?' : 'Bannir ce compte ?' ?>">
                <input type="hidden" name="csrf" value="<?= app_escape($csrf) ?>">
                <input type="hidden" name="action" value="<?= (int) $player['banned'] === 1 ? 'unban_account' : 'ban_account' ?>">
                <input type="hidden" name="player_id" value="<?= (int) $player['id'] ?>">
                <button class="<?= (int) $player['banned'] === 1 ? 'secondary-action' : 'danger-action' ?>" type="submit"<?= $editable ? '' : ' disabled' ?>>
                    <?= (int) $player['banned'] === 1 ? 'Débannir le compte' : 'Bannir le compte' ?>
                </button>
            </form>
        </section>

        <section class="admin-panel edit-section mini-audit">
            <div class="admin-panel-heading"><div><p class="panel-overline">Historique</p><h2>Actions récentes</h2></div></div>
            <div class="audit-list">
                <?php foreach ($audit as $entry): ?>
                    <article class="audit-entry <?= app_escape((string) $entry['outcome']) ?>">
                        <span class="audit-symbol"><?= (string) $entry['outcome'] === 'success' ? '✓' : '!' ?></span>
                        <div><strong><?= app_escape(admin_action_label((string) $entry['action'])) ?></strong><p><?= app_escape((string) $entry['summary']) ?></p></div>
                        <time><?= app_escape((new DateTimeImmutable((string) $entry['created_at']))->format('d/m H:i')) ?></time>
                    </article>
                <?php endforeach; ?>
                <?php if ($audit === []): ?><p class="quiet-copy">Aucune modification enregistrée.</p><?php endif; ?>
            </div>
        </section>
    </aside>
</div>

<?php admin_render_shell_end(); ?>

