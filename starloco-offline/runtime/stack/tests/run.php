<?php
declare(strict_types=1);
require_once __DIR__ . '/../web/public/app/bootstrap.php';
require_once __DIR__ . '/../web/public/app/admin.php';

final class FakeAccountRepository implements AccountRepository
{
    /** @var array<int,array<string,string|int>> */
    public array $rows = [];

    public function create(ValidatedAccount $account, string $passwordHash): int
    {
        foreach ($this->rows as $row) {
            if (strcasecmp((string) $row['account'], $account->account) === 0) {
                throw new AccountConflictException('account');
            }
            if (strcasecmp((string) $row['email'], $account->email) === 0) {
                throw new AccountConflictException('email');
            }
            if (strcasecmp((string) $row['pseudo'], $account->pseudo) === 0) {
                throw new AccountConflictException('pseudo');
            }
        }
        $id = count($this->rows) + 1;
        $this->rows[] = [
            'id' => $id,
            'account' => $account->account,
            'email' => $account->email,
            'pseudo' => $account->pseudo,
            'pass' => $passwordHash,
        ];
        return $id;
    }
}

$tests = [];
$test = static function (string $name, callable $callback) use (&$tests): void {
    $tests[] = [$name, $callback];
};
$assert = static function (bool $condition, string $message = 'Assertion échouée'): void {
    if (!$condition) {
        throw new RuntimeException($message);
    }
};
$assertThrows = static function (
    callable $callback,
    string $expectedClass = Throwable::class,
) use ($assert): Throwable {
    try {
        $callback();
    } catch (Throwable $exception) {
        $assert(
            $exception instanceof $expectedClass,
            sprintf('Exception %s reçue, %s attendue.', $exception::class, $expectedClass),
        );
        return $exception;
    }
    throw new RuntimeException("L'exception $expectedClass attendue n'a pas été levée.");
};

$test('hachage compatible avec le compte test historique', static function () use ($assert): void {
    $hash = (new LegacyPasswordHasher())->hash('test');
    $assert($hash === 'ff594f8cf10ca2e3ad4279375f0d0e688a7eca861000e7ecc63ae4b105c8be7bcb57e8c1172ea460c462c6f715508dc356fd964cf41644682db1feffd466769a');
    $assert(strlen($hash) === 128);
});

$test('création valide et normalisation', static function () use ($assert): void {
    $repository = new FakeAccountRepository();
    $service = new AccountService(new AccountValidator(), new LegacyPasswordHasher(), $repository);
    $result = $service->register(new AccountInput(
        'Joueur_Test', 'Joueur Test', 'PLAYER@EXAMPLE.COM', 'Secret123', 'Secret123',
        'Mon premier familier ?', 'Chacha',
    ));
    $assert($result['ok'] === true);
    $assert($repository->rows[0]['account'] === 'joueur_test');
    $assert($repository->rows[0]['email'] === 'player@example.com');
    $assert($repository->rows[0]['pseudo'] === 'Joueur Test');
});

$test('contrat exact des noms de compte du client et du portail', static function () use ($assert): void {
    $validator = new AccountValidator();
    $validNames = [
        'abc',
        'Retro_1419.test-account',
        'a23456789012345678901234567890',
    ];
    foreach ($validNames as $index => $account) {
        $result = $validator->validate(new AccountInput(
            $account, 'Joueur Valide', "valid$index@example.com",
            'Secret123', 'Secret123', 'Question valide ?', 'Réponse',
        ));
        $assert(!isset($result->errors['account']), "Nom de compte valide rejeté : $account");
    }

    $invalidNames = [
        '_retro',
        'ab',
        'a234567890123456789012345678901',
        'retro@1419',
        'rétro1419',
    ];
    foreach ($invalidNames as $index => $account) {
        $result = $validator->validate(new AccountInput(
            $account, 'Joueur Valide', "invalid$index@example.com",
            'Secret123', 'Secret123', 'Question valide ?', 'Réponse',
        ));
        $assert(isset($result->errors['account']), "Nom de compte invalide accepté : $account");
    }

    $spacedInput = AccountInput::fromArray([
        'account' => ' retro_1419 ',
        'pseudo' => 'Joueur Valide',
        'email' => 'spaced@example.com',
        'password' => 'Secret123',
        'password_confirmation' => 'Secret123',
        'question' => 'Question valide ?',
        'answer' => 'Réponse',
    ]);
    $assert(isset($validator->validate($spacedInput)->errors['account']),
        'Le prétraitement HTTP ne doit pas masquer les espaces hors contrat');

    $register = file_get_contents(__DIR__ . '/../web/public/register.php');
    $assert(is_string($register));
    $assert(str_contains($register, 'pattern="[A-Za-z0-9][A-Za-z0-9_.-]{2,29}"'));
});

$test('question et réponse rejettent tous les contrôles C0 et DEL', static function () use ($assert): void {
    $validator = new AccountValidator();
    $controls = array_merge(range(0, 31), [127]);

    foreach ($controls as $code) {
        $control = chr($code);
        $input = AccountInput::fromArray([
            'account' => 'joueurctrl',
            'pseudo' => 'Joueur Controle',
            'email' => 'control@example.com',
            'password' => 'Secret123',
            'password_confirmation' => 'Secret123',
            'question' => $control . 'Question valide',
            'answer' => 'Reponse' . $control,
        ]);
        $result = $validator->validate($input);
        $assert(isset($result->errors['question']), "Contrôle 0x" . sprintf('%02X', $code) . ' accepté dans la question');
        $assert(isset($result->errors['answer']), "Contrôle 0x" . sprintf('%02X', $code) . ' accepté dans la réponse');
    }
});

$test('rejet des doublons compte, email et pseudo', static function () use ($assert): void {
    $repository = new FakeAccountRepository();
    $service = new AccountService(new AccountValidator(), new LegacyPasswordHasher(), $repository);
    $base = new AccountInput('joueur1', 'Joueur Un', 'one@example.com', 'Secret123', 'Secret123', 'Question valide ?', 'Réponse');
    $assert($service->register($base)['ok'] === true);

    $duplicateAccount = $service->register(new AccountInput('JOUEUR1', 'Joueur Deux', 'two@example.com', 'Secret123', 'Secret123', 'Question valide ?', 'Réponse'));
    $assert(($duplicateAccount['errors']['account'] ?? '') !== '');

    $duplicateEmail = $service->register(new AccountInput('joueur2', 'Joueur Deux', 'ONE@example.com', 'Secret123', 'Secret123', 'Question valide ?', 'Réponse'));
    $assert(($duplicateEmail['errors']['email'] ?? '') !== '');

    $duplicatePseudo = $service->register(new AccountInput('joueur3', 'joueur un', 'three@example.com', 'Secret123', 'Secret123', 'Question valide ?', 'Réponse'));
    $assert(($duplicatePseudo['errors']['pseudo'] ?? '') !== '');
});

$test('validation complète des champs', static function () use ($assert): void {
    $validator = new AccountValidator();
    $result = $validator->validate(new AccountInput(
        'ad', '!!', 'mail-invalide', 'short', 'different', 'x', '',
    ));
    foreach (['account', 'pseudo', 'email', 'password', 'password_confirmation', 'question', 'answer'] as $field) {
        $assert(isset($result->errors[$field]), "Erreur attendue pour $field");
    }
});

$test('mot de passe limité au sel de chiffrement Retro', static function () use ($assert): void {
    $validator = new AccountValidator();
    foreach (['Abcdefg1', str_repeat('A', 31) . '1'] as $index => $acceptedPassword) {
        $accepted = $validator->validate(new AccountInput(
            "joueurok$index", "Joueur Limite $index", "accepted$index@example.com",
            $acceptedPassword, $acceptedPassword, 'Question valide ?', 'Réponse',
        ));
        $assert(!isset($accepted->errors['password']),
            'Les bornes inclusives de 8 et 32 caractères doivent être acceptées');
    }

    $tooShort = 'Abcdef1';
    $shortResult = $validator->validate(new AccountInput(
        'joueur7', 'Joueur Sept', 'short@example.com',
        $tooShort, $tooShort, 'Question valide ?', 'Réponse',
    ));
    $assert(isset($shortResult->errors['password']));

    $tooLong = str_repeat('a', 32) . '1';
    $result = $validator->validate(new AccountInput(
        'joueur32', 'Joueur Trente Deux', 'limit@example.com',
        $tooLong, $tooLong, 'Question valide ?', 'Réponse',
    ));
    $assert(isset($result->errors['password']));

    $unicode = 'Secret123🔒';
    $unsupported = $validator->validate(new AccountInput(
        'joueurutf', 'Joueur Unicode', 'unicode@example.com',
        $unicode, $unicode, 'Question valide ?', 'Réponse',
    ));
    $assert(isset($unsupported->errors['password']));

    $register = file_get_contents(__DIR__ . '/../web/public/register.php');
    $assert(is_string($register));
    $assert(substr_count($register, 'minlength="8"') >= 2);
    $assert(substr_count($register, 'maxlength="32"') >= 2);
});

$test('noms réservés interdits', static function () use ($assert): void {
    $result = (new AccountValidator())->validate(new AccountInput(
        'admin', 'Support', 'admin@example.com', 'Secret123', 'Secret123', 'Question valide ?', 'Réponse',
    ));
    $assert(isset($result->errors['account']));
    $assert(isset($result->errors['pseudo']));
});

$test('contrat SQL world_accounts cohérent', static function () use ($assert): void {
    $sqlPath = __DIR__ . '/../db-init/02-login.sql';
    $sql = file_get_contents($sqlPath);
    $assert(is_string($sql), 'Schéma login introuvable');
    $start = strpos($sql, 'CREATE TABLE `world_accounts`');
    $end = $start === false ? false : strpos($sql, ';', $start);
    $assert($start !== false && $end !== false, 'Table world_accounts introuvable');
    $table = substr($sql, $start, $end - $start);
    foreach (['guid', 'account', 'pass', 'email', 'question', 'reponse', 'pseudo', 'dateRegister'] as $column) {
        $assert(str_contains($table, '`' . $column . '`'), "Colonne $column absente");
    }
    $assert(!str_contains($table, '`email_token`'), 'Le schéma historique ne contient pas email_token');
});

$test('classement des conflits SQL par champ', static function () use ($assert): void {
    $emailError = new PDOException("Duplicate entry 'x@example.com' for key 'uq_world_accounts_email'");
    $emailError->errorInfo = ['23000', 1062, "Duplicate entry for key 'uq_world_accounts_email'"];
    $assert(SqlConflictClassifier::field($emailError) === 'email');

    $pseudoError = new PDOException("Duplicate entry 'Pseudo' for key 'uq_world_accounts_pseudo'");
    $pseudoError->errorInfo = ['23000', 1062, "Duplicate entry for key 'uq_world_accounts_pseudo'"];
    $assert(SqlConflictClassifier::field($pseudoError) === 'pseudo');

    $accountError = new PDOException("Duplicate entry 'joueur' for key 'account'");
    $assert(SqlConflictClassifier::field($accountError) === 'account');
});

$test('migration de compte relançable et date normalisée', static function () use ($assert): void {
    $source = file_get_contents(__DIR__ . '/../db-init/05-account-hardening.sql');
    $assert(is_string($source));
    $assert(str_contains($source, "column_name = 'email'"));
    $assert(str_contains($source, "column_name = 'pseudo'"));
    $assert(str_contains($source, "STR_TO_DATE"));
    $assert(str_contains($source, "DATE_FORMAT"));

    $cleanup = file_get_contents(__DIR__ . '/../db-init/06-remove-demo-accounts.sql');
    $assert(is_string($cleanup));
    $assert(str_contains($cleanup, 'DELETE `player`'));
    $assert(str_contains($cleanup, 'Bredravorveidurroth'));

    $lock = file_get_contents(__DIR__ . '/../db-init/06-disable-public-demo-accounts.sql');
    $assert(is_string($lock));
    $assert(str_contains($lock, 'UPDATE `world_accounts`'));
    $assert(str_contains($lock, '`banned` = 1'));
    $assert(!str_contains($lock, 'DELETE'));

    $migratorPath = __DIR__ . '/../../../offline-migrate.sh';
    if (!is_file($migratorPath)) {
        $migratorPath = __DIR__ . '/../offline-migrate.sh';
    }
    $migrator = file_get_contents($migratorPath);
    $assert(is_string($migrator));
    $assert(str_contains($migrator, '06-disable-public-demo-accounts.sql'));
    $assert(!str_contains($migrator, '06-remove-demo-accounts.sql'));
});

$test('validation invalide sans connexion à la base', static function () use ($assert): void {
    $called = false;
    $repository = new LazyAccountRepository(static function () use (&$called): PDO {
        $called = true;
        throw new RuntimeException('La base ne doit pas être appelée.');
    });
    $service = new AccountService(new AccountValidator(), new LegacyPasswordHasher(), $repository);
    $result = $service->register(new AccountInput(
        'x', 'x', 'invalide', 'court', 'différent', 'x', '',
    ));
    $assert($result['ok'] === false);
    $assert($called === false);
});

$test('succès protégé contre la resoumission du formulaire', static function () use ($assert): void {
    $register = file_get_contents(__DIR__ . '/../web/public/register.php');
    $assert(is_string($register));
    $assert(str_contains($register, "header('Location: /register.php', true, 303)"));
    $assert(str_contains($register, "registration_success"));
});

$test('déploiement web cohérent et source non exposée', static function () use ($assert): void {
    $nginx = file_get_contents(__DIR__ . '/../web/nginx.conf');
    $compose = file_get_contents(__DIR__ . '/../docker-compose.yml');
    $assert(is_string($nginx) && is_string($compose));
    $assert(str_contains($nginx, 'root /src/public;'));
    $assert(str_contains($nginx, 'location ^~ /app/ { deny all; }'));
    $assert(str_contains($nginx, 'location = /cli-create-account.php { deny all; }'));
    $assert(str_contains($nginx, 'location = /api/feed.php'));
    $assert(str_contains($compose, 'social-worker:'));
    $assert(str_contains($compose, 'cli-social-worker.php'));
    $assert(str_contains($compose, 'GAME_SERVER_KEY: ${GAME_SERVER_KEY:?GAME_SERVER_KEY absent dans .env}'));
});

$test('aucun secret de mot de passe dans un cookie applicatif', static function () use ($assert): void {
    $register = file_get_contents(__DIR__ . '/../web/public/register.php');
    $bootstrap = file_get_contents(__DIR__ . '/../web/public/app/bootstrap.php');
    $assert(is_string($register) && is_string($bootstrap));
    $assert(!str_contains(strtolower($register), 'setcookie('));
    $assert(!str_contains(strtolower($bootstrap), "setcookie('password"));
    $assert(str_contains($bootstrap, "'httponly' => true"));
    $assert(str_contains($bootstrap, "'samesite' => 'Lax'"));
});

$test('assertions SQL du schéma présentes', static function () use ($assert): void {
    $sql = file_get_contents(__DIR__ . '/../db-init/07-account-schema-assertions.sql');
    $assert(is_string($sql));
    $assert(str_contains($sql, "table_engine <> 'InnoDB'"));
    $assert(str_contains($sql, "correct_columns <> 7"));
    $assert(str_contains($sql, "SIGNAL SQLSTATE '45000'"));
});

$test('le portail n’insère aucune colonne fantôme', static function () use ($assert): void {
    $source = file_get_contents(__DIR__ . '/../web/public/app/bootstrap.php');
    $assert(is_string($source));
    $assert(!str_contains($source, 'email_token'));
    $assert(str_contains($source, '(account, pass, email, question, reponse, pseudo, dateRegister'));
});



$test('analyse robuste des identifiants d’inventaire', static function () use ($assert): void {
    require_once __DIR__ . '/../web/public/app/social.php';
    $assert(social_parse_object_ids('12|45,78;12') === [12, 45, 78]);
    $assert(social_parse_object_ids('') === []);
});

$test('seuil des objets remarquables configurable', static function () use ($assert): void {
    require_once __DIR__ . '/../web/public/app/social.php';
    $config = new SocialConfig(15, 60, 50000, 1, 90, 5000);
    $assert(social_is_notable_item(['level' => 60, 'avgPrice' => 0, 'points' => 0], $config));
    $assert(social_is_notable_item(['level' => 1, 'avgPrice' => 50000, 'points' => 0], $config));
    $assert(!social_is_notable_item(['level' => 10, 'avgPrice' => 100, 'points' => 0], $config));
});

$test('migration complète du fil communautaire', static function () use ($assert): void {
    $sql = file_get_contents(__DIR__ . '/../db-init/08-social-feed.sql');
    $assert(is_string($sql));
    foreach (['website_social_events', 'website_social_player_snapshots', 'website_social_inventory_snapshots', 'website_social_quest_snapshots', 'website_social_state'] as $table) {
        $assert(str_contains($sql, "`$table`"), "Table sociale absente : $table");
    }
    $assert(str_contains($sql, 'UNIQUE KEY `uq_social_event_key`'));
});

$test('le fil ne lit aucune donnée privée des comptes', static function () use ($assert): void {
    $social = file_get_contents(__DIR__ . '/../web/public/app/social.php');
    $index = file_get_contents(__DIR__ . '/../web/public/index.php');
    $assert(is_string($social) && is_string($index));
    $assert(!str_contains($social, 'world_accounts'));
    $assert(str_contains($social, 'starloco_game.quest_progress'));
    $assert(str_contains($social, 'world_players_quests'));
    $assert(!str_contains(strtolower($index), 'email'));
    $assert(str_contains($index, '/register.php'));
});

$test('validation stricte des entiers de la console administrateur', static function () use ($assert, $assertThrows): void {
    $assert(admin_integer('0', 0, 10, 'Valeur') === 0);
    $assert(admin_integer('10', 0, 10, 'Valeur') === 10);
    $assert(admin_integer('-5', -5, 5, 'Valeur') === -5);
    $assert(admin_integer('001', 0, 10, 'Valeur') === 1);

    foreach (['', ' 1', '1 ', '+1', '1.0', '1e2', '0x10', '999999999999999999999999999'] as $invalid) {
        $assertThrows(
            static fn (): int => admin_integer($invalid, -10, 10, 'Valeur'),
            AdminOperationException::class,
        );
    }
    foreach ([[], null] as $invalid) {
        $assertThrows(
            static fn (): int => admin_integer($invalid, -10, 10, 'Valeur'),
            AdminOperationException::class,
        );
    }
    foreach (['-11', '11'] as $outsideBounds) {
        $assertThrows(
            static fn (): int => admin_integer($outsideBounds, -10, 10, 'Valeur'),
            AdminOperationException::class,
        );
    }
});

$test('compatibilité et génération prudente des statistiques d’objets', static function () use ($assert): void {
    $assert(admin_item_type_label(1) === 'Amulette');
    $assert(admin_item_type_label(999) === 'Type 999');

    $normal = admin_item_support(100, 1);
    $assert($normal['supported'] === true && $normal['reason'] === null);
    foreach ([admin_item_support(100, 18), admin_item_support(6653, 1)] as $special) {
        $assert($special['supported'] === false);
        $assert(is_string($special['reason']) && $special['reason'] !== '');
    }

    $template = '7d#1#a#0#1d10+0,64#1#a#0#1d10+0,119#1#a#0#1d10+0,7e#2#a#1#text,bad';
    $assert(admin_item_stats($template, 'base') === $template);
    $assert(
        admin_item_stats($template, 'perfect')
        === '7d#a#a#0#1d10+0,64#1#a#0#1d10+0,119#1#a#0#1d10+0,7e#2#a#1#text,bad',
        'Seules les statistiques numériques ordinaires doivent être maximisées.',
    );
    $assert(admin_item_stats('', 'perfect') === '');
});

$test('capacités administrateur fermées par défaut', static function () use ($assert): void {
    $sessionWasDefined = array_key_exists('_SESSION', $GLOBALS);
    $previousSession = $GLOBALS['_SESSION'] ?? null;
    try {
        $_SESSION = ['admin_role' => 'owner'];
        foreach (['dashboard.view', 'players.view', 'players.edit', 'accounts.moderate', 'audit.view'] as $capability) {
            $assert(admin_can($capability), "Capacité propriétaire absente : $capability");
        }
        $assert(!admin_can('system.shell'), 'Une capacité inconnue ne doit jamais être accordée.');

        foreach (['viewer', ''] as $role) {
            $_SESSION = ['admin_role' => $role];
            $assert(!admin_can('dashboard.view'), "Le rôle $role ne doit recevoir aucune capacité.");
            $assert(!admin_can('players.edit'), "Le rôle $role ne doit pas pouvoir modifier un personnage.");
        }
    } finally {
        if ($sessionWasDefined) {
            $GLOBALS['_SESSION'] = $previousSession;
        } else {
            unset($GLOBALS['_SESSION']);
        }
    }
});

$test('secrets administrateur Argon2id et HMAC en échec fermé', static function () use ($assert, $assertThrows): void {
    $assert(defined('PASSWORD_ARGON2ID'), 'Le runtime PHP doit prendre en charge Argon2id.');
    $hashFile = tempnam(sys_get_temp_dir(), 'starloco-admin-hash-');
    $keyFile = tempnam(sys_get_temp_dir(), 'starloco-admin-hmac-');
    $assert(is_string($hashFile) && is_string($keyFile), 'Impossible de créer les secrets de test temporaires.');
    $password = 'Admin-Test-Secret-1419';
    $hash = password_hash($password, PASSWORD_ARGON2ID, [
        'memory_cost' => 8192,
        'time_cost' => 1,
        'threads' => 1,
    ]);
    $assert(is_string($hash) && str_starts_with($hash, '$argon2id$'));
    $hmacKey = str_repeat('ab', 32);
    $previousRemoteWasDefined = array_key_exists('REMOTE_ADDR', $_SERVER);
    $previousRemote = $_SERVER['REMOTE_ADDR'] ?? null;

    try {
        $assert(file_put_contents($hashFile, $hash . "\n") !== false);
        $assert(file_put_contents($keyFile, $hmacKey . "\n") !== false);
        $config = new AdminConfig('admin-test', $hashFile, $keyFile);
        $assert($config->passwordHash() === $hash);
        $assert(password_verify($password, $config->passwordHash()));
        $assert(!password_verify('mauvais-secret', $config->passwordHash()));
        $assert($config->hmacKey() === $hmacKey);

        $_SERVER['REMOTE_ADDR'] = '192.0.2.44';
        $sourceHash = admin_source_hash($config);
        $assert($sourceHash === hash_hmac('sha256', '192.0.2.44', $hmacKey));
        $assert($sourceHash !== hash('sha256', '192.0.2.44'));

        $assert(file_put_contents($hashFile, password_hash($password, PASSWORD_BCRYPT)) !== false);
        $assertThrows(static fn (): string => $config->passwordHash(), RuntimeException::class);
        $assert(file_put_contents($keyFile, 'trop-court') !== false);
        $assertThrows(static fn (): string => $config->hmacKey(), RuntimeException::class);

        $missing = sys_get_temp_dir() . '/starloco-admin-missing-' . bin2hex(random_bytes(8));
        $missingConfig = new AdminConfig('admin-test', $missing, $missing);
        $assertThrows(static fn (): string => $missingConfig->passwordHash(), RuntimeException::class);
        $assertThrows(static fn (): string => $missingConfig->hmacKey(), RuntimeException::class);
    } finally {
        if ($previousRemoteWasDefined) {
            $_SERVER['REMOTE_ADDR'] = $previousRemote;
        } else {
            unset($_SERVER['REMOTE_ADDR']);
        }
        if (is_string($hashFile) && is_file($hashFile)) {
            unlink($hashFile);
        }
        if (is_string($keyFile) && is_file($keyFile)) {
            unlink($keyFile);
        }
    }
});

$test('routes et en-têtes de la console administrateur restent verrouillés', static function () use ($assert): void {
    $public = __DIR__ . '/../web/public';
    $routes = [
        'index.php' => false,
        'login.php' => false,
        'logout.php' => true,
        'player.php' => true,
        'action.php' => true,
        'api/items.php' => true,
    ];
    foreach ($routes as $route => $protected) {
        $source = file_get_contents("$public/admin/$route");
        $assert(is_string($source), "Route administrateur absente : $route");
        $assert(str_contains($source, 'app_security_headers();'), "En-têtes de sécurité absents : $route");
        if ($protected) {
            $assert(str_contains($source, 'admin_require_auth('), "Authentification absente : $route");
        }
    }

    foreach (['logout.php', 'action.php'] as $route) {
        $source = file_get_contents("$public/admin/$route");
        $assert(is_string($source));
        $assert(str_contains($source, "REQUEST_METHOD"));
        $assert(str_contains($source, "'POST'"));
        $assert(str_contains($source, 'admin_verify_csrf('));
    }
    $itemsApi = file_get_contents("$public/admin/api/items.php");
    $assert(is_string($itemsApi) && str_contains($itemsApi, 'admin_require_auth(true);'));

    foreach ([
        "$public/app/admin.php",
        "$public/app/admin-layout.php",
        "$public/assets/admin.css",
        "$public/assets/admin.js",
    ] as $asset) {
        $assert(is_file($asset), 'Fichier de console absent : ' . basename($asset));
    }

    $bootstrap = file_get_contents("$public/app/bootstrap.php");
    $assert(is_string($bootstrap));
    foreach ([
        "X-Content-Type-Options: nosniff",
        "X-Frame-Options: DENY",
        "frame-ancestors 'none'",
        "Cache-Control: no-store",
    ] as $headerContract) {
        $assert(str_contains($bootstrap, $headerContract), "En-tête de sécurité absent : $headerContract");
    }

    $admin = file_get_contents("$public/app/admin.php");
    $assert(is_string($admin));
    foreach (["'path' => '/admin'", "'httponly' => true", "'samesite' => 'Strict'", 'session_regenerate_id(true)'] as $sessionContract) {
        $assert(str_contains($admin, $sessionContract), "Contrat de session absent : $sessionContract");
    }

    $javascript = file_get_contents("$public/assets/admin.js");
    $assert(is_string($javascript) && str_contains($javascript, 'textContent'));
    foreach (['innerHTML', 'outerHTML', 'insertAdjacentHTML'] as $unsafeSink) {
        $assert(!str_contains($javascript, $unsafeSink), "Construction DOM non sûre détectée : $unsafeSink");
    }

    $nginx = file_get_contents(__DIR__ . '/../web/nginx.conf');
    $assert(is_string($nginx));
    foreach ([
        'limit_req_zone $binary_remote_addr zone=admin_login:',
        'limit_req_zone $binary_remote_addr zone=admin_api:',
        'limit_req_status 429;',
        'location = /admin/',
        'location = /admin/login',
        'location = /admin/logout',
        'location = /admin/player',
        'location = /admin/action',
        'location = /admin/api/items',
        'location ^~ /admin/ { return 404; }',
    ] as $routeContract) {
        $assert(str_contains($nginx, $routeContract), "Contrat Nginx absent : $routeContract");
    }
});

$test('rechargement administrateur consommé exclusivement par le serveur Game', static function () use ($assert): void {
    $root = realpath(__DIR__ . '/../../..');
    $assert(is_string($root), 'Racine du projet introuvable.');
    $exchange = file_get_contents(
        "$root/serveur-jeu/src/org/starloco/locos/exchange/ExchangePacketHandler.java",
    );
    $gameAccountData = file_get_contents(
        "$root/serveur-jeu/src/org/starloco/locos/database/data/login/AccountData.java",
    );
    $loginAccountData = file_get_contents(
        "$root/sources/StarLoco-Login/src/org/starloco/locos/database/data/AccountData.java",
    );
    $admin = file_get_contents(__DIR__ . '/../web/public/app/admin.php');
    $assert(
        is_string($exchange) && is_string($gameAccountData)
        && is_string($loginAccountData) && is_string($admin),
        'Sources du contrat de rechargement introuvables.',
    );

    $needs = strpos($exchange, 'boolean reloadRequested = accountData.needsReload(id);');
    $conditionalLoad = strpos($exchange, '? accountData.load(id)');
    $nullGuard = strpos($exchange, 'if (account == null)');
    $clear = strpos($exchange, 'accountData.clearReloadNeeded(id);');
    $assert($needs !== false && $conditionalLoad !== false && $nullGuard !== false && $clear !== false);
    $assert(
        $needs < $conditionalLoad && $conditionalLoad < $nullGuard && $nullGuard < $clear,
        'Le Game doit effacer reload_needed uniquement après avoir rechargé un compte valide.',
    );

    $assert(str_contains($gameAccountData, 'public boolean needsReload(int id)'));
    $assert(str_contains($gameAccountData, 'public void clearReloadNeeded(int id)'));
    $assert(str_contains($gameAccountData, 'SET `reload_needed` = 0'));
    $assert(!str_contains($loginAccountData, 'clearReloadNeeded'));
    $assert(!str_contains($loginAccountData, 'SET `reload_needed`'));

    $assert(str_contains($admin, 'private function markAccountForReload(int $accountId)'));
    $assert(str_contains($admin, 'UPDATE world_accounts SET reload_needed = 1 WHERE guid = :id'));
    $assert(str_contains($admin, 'FOR UPDATE'));
    $assert(
        str_contains($admin, "(int) \$player['logged'] === 1 || (int) \$player['account_logged'] === 1"),
        'Les personnages conservés en mémoire doivent rester non modifiables depuis la console.',
    );
});

$test('combattant déconnecté conservé comme connecté jusqu’à son éviction', static function () use ($assert): void {
    $root = realpath(__DIR__ . '/../../..');
    $assert(is_string($root));
    $account = file_get_contents("$root/serveur-jeu/src/org/starloco/locos/client/Account.java");
    $player = file_get_contents("$root/serveur-jeu/src/org/starloco/locos/client/Player.java");
    $assert(is_string($account) && is_string($player));

    $disconnectStart = strpos($account, 'public void disconnect(Player player)');
    $disconnectEnd = $disconnectStart === false
        ? false
        : strpos($account, 'public void updateVote', $disconnectStart);
    $assert($disconnectStart !== false && $disconnectEnd !== false);
    $disconnect = substr($account, $disconnectStart, $disconnectEnd - $disconnectStart);
    $retained = strpos($disconnect, 'onPlayerDisconnection(player, false)');
    $loggedOne = strpos($disconnect, 'updateLogged(player.getId(), 1)');
    $returnAfterRetention = $loggedOne === false ? false : strpos($disconnect, 'return;', $loggedOne);
    $loggedZero = strpos($disconnect, 'updateLogged(player.getId(), 0)');
    $assert(
        $retained !== false && $loggedOne !== false && $returnAfterRetention !== false && $loggedZero !== false,
        'Contrat de déconnexion en combat incomplet.',
    );
    $assert(
        $retained < $loggedOne && $loggedOne < $returnAfterRetention && $returnAfterRetention < $loggedZero,
        'Un combattant conservé doit rester logged=1 et quitter la déconnexion avant logged=0.',
    );

    $terminalStart = strpos($player, 'public void disconnectInFight()');
    $terminalEnd = $terminalStart === false
        ? false
        : strpos($player, 'public int getBankCost()', $terminalStart);
    $assert($terminalStart !== false && $terminalEnd !== false);
    $terminal = substr($player, $terminalStart, $terminalEnd - $terminalStart);
    $loggedZeroTerminal = strpos($terminal, 'updateLogged(this.id, 0)');
    $unload = strpos($terminal, 'World.world.unloadPerso(this.getId())');
    $assert($loggedZeroTerminal !== false && $unload !== false && $loggedZeroTerminal < $unload);
});

$test('aucune erreur interne du worker n’est exposée au portail public', static function () use ($assert): void {
    $social = file_get_contents(__DIR__ . '/../web/public/app/social.php');
    $feed = file_get_contents(__DIR__ . '/../web/public/api/feed.php');
    $javascript = file_get_contents(__DIR__ . '/../web/public/assets/app.js');
    $index = file_get_contents(__DIR__ . '/../web/public/index.php');
    $assert(is_string($social) && is_string($feed) && is_string($javascript) && is_string($index));

    $dashboardStart = strpos($social, 'function social_get_dashboard(PDO $pdo): array');
    $assert($dashboardStart !== false);
    $dashboardSource = substr($social, $dashboardStart);
    foreach (['last_error', 'lastError'] as $privateField) {
        $assert(
            !str_contains($dashboardSource, $privateField),
            "Le tableau de bord public expose le champ privé $privateField.",
        );
        $assert(!str_contains($feed, $privateField), "L'API publique expose le champ privé $privateField.");
        $assert(!str_contains($javascript, $privateField), "Le JavaScript public consomme le champ privé $privateField.");
    }
    $assert(
        !str_contains($index, "\$dashboard['worker']['lastError']"),
        'La page publique ne doit jamais afficher la dernière erreur interne du worker.',
    );
});

$failures = 0;
foreach ($tests as [$name, $callback]) {
    try {
        $callback();
        fwrite(STDOUT, "[OK] $name\n");
    } catch (Throwable $exception) {
        $failures++;
        fwrite(STDERR, "[ÉCHEC] $name: {$exception->getMessage()}\n");
    }
}

fwrite(STDOUT, sprintf("%d test(s), %d échec(s).\n", count($tests), $failures));
exit($failures === 0 ? 0 : 1);
