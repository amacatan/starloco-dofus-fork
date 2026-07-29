<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/admin.php';

app_security_headers();
if (admin_is_authenticated()) {
    header('Location: /admin/', true, 303);
    exit;
}

$error = null;
$username = AdminConfig::fromEnvironment()->username;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $submittedUsername = is_scalar($_POST['username'] ?? null) ? (string) $_POST['username'] : '';
    $password = is_scalar($_POST['password'] ?? null) ? (string) $_POST['password'] : '';
    $csrf = is_scalar($_POST['csrf'] ?? null) ? (string) $_POST['csrf'] : '';
    $website = is_scalar($_POST['website'] ?? null) ? (string) $_POST['website'] : '';
    $username = mb_substr($submittedUsername, 0, 64);

    if (!admin_verify_csrf($csrf) || $website !== '') {
        $error = 'La requête a expiré. Rechargez la page.';
    } elseif (strlen($submittedUsername) > 64 || strlen($password) > 256) {
        $error = 'Identifiants invalides.';
    } else {
        try {
            if (admin_authenticate(app_pdo(), AdminConfig::fromEnvironment(), $submittedUsername, $password)) {
                header('Location: /admin/', true, 303);
                exit;
            }
            usleep(350000);
            $error = 'Identifiants invalides.';
        } catch (AdminRateLimitException $exception) {
            $error = $exception->getMessage();
        } catch (Throwable $exception) {
            error_log('Admin login error: ' . $exception->getMessage());
            $error = 'La console est momentanément indisponible.';
        }
    }
}

$config = AppConfig::fromEnvironment();
?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="dark">
    <title>Connexion · <?= app_escape($config->serverName) ?> Control</title>
    <link rel="stylesheet" href="/assets/app.css">
    <link rel="stylesheet" href="/assets/admin.css">
</head>
<body class="admin-login-page">
<main class="admin-login-shell">
    <section class="login-showcase" aria-label="Présentation">
        <a class="control-brand login-brand" href="/">
            <span class="control-brand-mark">S</span>
            <span><strong>CONTROL</strong><small><?= app_escape($config->serverName) ?></small></span>
        </a>
        <div class="login-visual" aria-hidden="true">
            <span class="radar-ring ring-one"></span>
            <span class="radar-ring ring-two"></span>
            <span class="radar-ring ring-three"></span>
            <span class="radar-core">S</span>
            <span class="radar-ping ping-one"></span>
            <span class="radar-ping ping-two"></span>
            <span class="radar-line"></span>
        </div>
        <div>
            <p class="admin-kicker"><span></span> Centre de commandement</p>
            <h1>Le monde entre<br>vos mains.</h1>
            <p>Personnages, inventaires, économie et historique des actions réunis dans une console locale.</p>
        </div>
        <ul class="login-features">
            <li><span>01</span> Mutations atomiques</li>
            <li><span>02</span> Personnages hors ligne uniquement</li>
            <li><span>03</span> Journal d’audit permanent</li>
        </ul>
    </section>

    <section class="login-panel">
        <div class="login-panel-inner">
            <p class="login-overline">Accès propriétaire</p>
            <h2>Connexion sécurisée</h2>
            <p class="login-copy">Cette zone est accessible uniquement depuis la machine locale ou votre réseau privé.</p>

            <?php if ($error !== null): ?>
                <div class="admin-login-error" role="alert"><span>!</span><?= app_escape($error) ?></div>
            <?php endif; ?>

            <form method="post" action="/admin/login" class="admin-login-form">
                <input type="hidden" name="csrf" value="<?= app_escape(admin_csrf_token()) ?>">
                <div class="honeypot" aria-hidden="true">
                    <label for="website">Site web</label>
                    <input id="website" name="website" type="text" tabindex="-1" autocomplete="off">
                </div>
                <label for="username">Identifiant</label>
                <div class="control-input">
                    <span aria-hidden="true">◎</span>
                    <input id="username" name="username" type="text" maxlength="64"
                           value="<?= app_escape($username) ?>" autocomplete="username" required autofocus>
                </div>
                <label for="password">Mot de passe</label>
                <div class="control-input">
                    <span aria-hidden="true">⌁</span>
                    <input id="password" name="password" type="password" maxlength="256"
                           autocomplete="current-password" required>
                </div>
                <button class="control-submit" type="submit">
                    Ouvrir la console <span aria-hidden="true">→</span>
                </button>
            </form>

            <div class="login-help">
                <span aria-hidden="true">i</span>
                <p>Affichez vos accès avec <code>./offline-admin-password.sh</code>.</p>
            </div>
        </div>
        <a class="back-public" href="/">← Retour au portail public</a>
    </section>
</main>
</body>
</html>

