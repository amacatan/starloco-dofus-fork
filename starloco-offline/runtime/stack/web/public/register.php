<?php
declare(strict_types=1);
require_once __DIR__ . '/app/bootstrap.php';

app_security_headers();
app_start_session();
$config = AppConfig::fromEnvironment();
$errors = [];
$success = isset($_SESSION['registration_success']) && $_SESSION['registration_success'] === true;
unset($_SESSION['registration_success']);
$values = [
    'account' => '',
    'pseudo' => '',
    'email' => '',
    'question' => '',
];

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    foreach (array_keys($values) as $key) {
        $values[$key] = is_scalar($_POST[$key] ?? null) ? trim((string) $_POST[$key]) : '';
    }

    if (!app_rate_limit_allows()) {
        http_response_code(429);
        $errors['form'] = 'Trop de tentatives. Réessayez dans quelques minutes.';
    } elseif (!app_verify_csrf(is_scalar($_POST['csrf'] ?? null) ? (string) $_POST['csrf'] : '')) {
        http_response_code(400);
        $errors['form'] = 'La session du formulaire a expiré. Rechargez la page.';
    } elseif (!empty($_POST['website'])) {
        // Champ piège pour robots : réponse neutre, aucune insertion.
        $_SESSION['registration_success'] = true;
        header('Location: /register.php', true, 303);
        exit;
    } elseif (!isset($_POST['confirm_creation'])) {
        $errors['confirm_creation'] = 'Confirmez la création du compte de jeu.';
    } else {
        app_record_attempt();
        $input = AccountInput::fromArray($_POST);
        try {
            $result = app_service()->register($input);
            if ($result['ok']) {
                app_rotate_csrf();
                $_SESSION['registration_attempts'] = [];
                $_SESSION['registration_success'] = true;
                header('Location: /register.php', true, 303);
                exit;
            } else {
                $errors = $result['errors'] ?? ['form' => 'Les données sont invalides.'];
            }
        } catch (Throwable $exception) {
            error_log('Registration error: ' . $exception->getMessage());
            http_response_code(503);
            $errors['form'] = 'Le service d’inscription est temporairement indisponible.';
        }
    }
}

function field_error(array $errors, string $field): string
{
    return isset($errors[$field]) ? '<p class="field-error" id="error-' . app_escape($field) . '">' . app_escape($errors[$field]) . '</p>' : '';
}

function described_by(array $errors, string $field): string
{
    return isset($errors[$field]) ? ' aria-invalid="true" aria-describedby="error-' . app_escape($field) . '"' : '';
}
?>
<!doctype html>
<html lang="fr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Créer un compte — <?= app_escape($config->serverName) ?></title>
    <link rel="stylesheet" href="/assets/app.css">
</head>
<body class="registration-page">
<header class="site-header">
    <a class="brand" href="/" aria-label="Retour au fil communautaire">
        <span class="brand-mark">S</span>
        <span><strong><?= app_escape($config->serverName) ?></strong><small>Retro 1.41.9</small></span>
    </a>
    <nav class="top-nav" aria-label="Navigation principale">
        <a href="/">Fil des aventuriers</a>
        <a href="/players">Annuaire</a>
        <a class="button-link compact active" href="/register.php">Créer un compte</a>
    </nav>
</header>
<main class="page-shell registration-shell">
    <section class="hero" aria-labelledby="title">
        <p class="eyebrow">Rejoindre la communauté</p>
        <h1 id="title">Créer un compte de jeu</h1>
        <p>Créez vos identifiants de connexion. Votre nom de compte et votre e-mail ne seront jamais affichés dans le fil communautaire.</p>
    </section>

    <section class="panel">
        <?php if ($success): ?>
            <div class="notice success" role="status">
                <strong>Compte créé.</strong> Vous pouvez maintenant vous connecter au serveur avec votre nom de compte et votre mot de passe.
            </div>
        <?php endif; ?>
        <?php if (isset($errors['form'])): ?>
            <div class="notice error" role="alert"><?= app_escape($errors['form']) ?></div>
        <?php endif; ?>

        <form method="post" action="/register.php" novalidate autocomplete="off">
            <input type="hidden" name="csrf" value="<?= app_escape(app_csrf_token()) ?>">
            <div class="honeypot" aria-hidden="true">
                <label>Site web <input type="text" name="website" tabindex="-1" autocomplete="off"></label>
            </div>

            <div class="grid">
                <div class="field">
                    <label for="account">Nom de compte</label>
                    <input id="account" name="account" value="<?= app_escape($values['account']) ?>" minlength="3" maxlength="30" pattern="[A-Za-z0-9][A-Za-z0-9_.-]{2,29}" required autocomplete="username"<?= described_by($errors, 'account') ?>>
                    <small>3–30 caractères, sans espace.</small>
                    <?= field_error($errors, 'account') ?>
                </div>

                <div class="field">
                    <label for="pseudo">Pseudo public</label>
                    <input id="pseudo" name="pseudo" value="<?= app_escape($values['pseudo']) ?>" minlength="3" maxlength="30" required<?= described_by($errors, 'pseudo') ?>>
                    <small>Visible par les autres joueurs.</small>
                    <?= field_error($errors, 'pseudo') ?>
                </div>

                <div class="field full">
                    <label for="email">Adresse e-mail</label>
                    <input id="email" type="email" name="email" value="<?= app_escape($values['email']) ?>" maxlength="100" required autocomplete="email"<?= described_by($errors, 'email') ?>>
                    <?= field_error($errors, 'email') ?>
                </div>

                <div class="field">
                    <label for="password">Mot de passe</label>
                    <input id="password" type="password" name="password" minlength="8" maxlength="32" required autocomplete="new-password"<?= described_by($errors, 'password') ?>>
                    <small>8–32 caractères latins, avec une lettre et un chiffre.</small>
                    <?= field_error($errors, 'password') ?>
                </div>

                <div class="field">
                    <label for="password_confirmation">Confirmation</label>
                    <input id="password_confirmation" type="password" name="password_confirmation" minlength="8" maxlength="32" required autocomplete="new-password"<?= described_by($errors, 'password_confirmation') ?>>
                    <?= field_error($errors, 'password_confirmation') ?>
                </div>

                <div class="field">
                    <label for="question">Question secrète</label>
                    <input id="question" name="question" value="<?= app_escape($values['question']) ?>" minlength="5" maxlength="100" required<?= described_by($errors, 'question') ?>>
                    <?= field_error($errors, 'question') ?>
                </div>

                <div class="field">
                    <label for="answer">Réponse secrète</label>
                    <input id="answer" name="answer" minlength="2" maxlength="100" required autocomplete="off"<?= described_by($errors, 'answer') ?>>
                    <?= field_error($errors, 'answer') ?>
                </div>
            </div>

            <label class="confirmation">
                <input type="checkbox" name="confirm_creation" value="1" required<?= isset($errors['confirm_creation']) ? ' aria-invalid="true"' : '' ?>>
                <span>Je confirme vouloir créer ce compte de jeu.</span>
            </label>
            <?= field_error($errors, 'confirm_creation') ?>

            <button type="submit">Créer mon compte</button>
        </form>
    </section>
</main>
</body>
</html>
