<?php
declare(strict_types=1);

final class AppConfig
{
    public function __construct(
        public readonly string $dbHost,
        public readonly int $dbPort,
        public readonly string $dbName,
        public readonly string $dbUser,
        public readonly string $dbPassword,
        public readonly string $serverName,
    ) {}

    public static function fromEnvironment(): self
    {
        $password = (string) getenv('STARLOCO_DB_PASSWORD');
        $passwordFile = (string) (getenv('STARLOCO_DB_PASSWORD_FILE') ?: '/run/secrets/starloco_db_password_secret');
        if ($password === '' && is_readable($passwordFile)) {
            $password = trim((string) file_get_contents($passwordFile));
        }

        return new self(
            dbHost: (string) (getenv('MARIADB_HOST') ?: 'mariadb'),
            dbPort: max(1, (int) (getenv('MARIADB_PORT') ?: 3306)),
            dbName: (string) (getenv('MARIADB_DATABASE') ?: 'starloco_login'),
            dbUser: (string) (getenv('STARLOCO_DB_USER') ?: 'starloco'),
            dbPassword: $password,
            serverName: (string) (getenv('GAME_SERVER_NAME') ?: 'StarLoco'),
        );
    }
}

final class AccountInput
{
    public function __construct(
        public string $account,
        public string $pseudo,
        public string $email,
        public string $password,
        public string $passwordConfirmation,
        public string $question,
        public string $answer,
    ) {}

    /** @param array<string,mixed> $source */
    public static function fromArray(array $source): self
    {
        $rawValue = static fn(string $key): string => is_scalar($source[$key] ?? null)
            ? (string) $source[$key]
            : '';
        $trimmedValue = static fn(string $key): string => trim($rawValue($key));

        return new self(
            // Authentication identifiers and recovery fields must reach the
            // validator byte-for-byte: trimming would hide forbidden controls.
            account: $rawValue('account'),
            pseudo: $trimmedValue('pseudo'),
            email: $trimmedValue('email'),
            password: is_scalar($source['password'] ?? null) ? (string) $source['password'] : '',
            passwordConfirmation: is_scalar($source['password_confirmation'] ?? null) ? (string) $source['password_confirmation'] : '',
            question: $rawValue('question'),
            answer: $rawValue('answer'),
        );
    }
}

final class ValidatedAccount
{
    public function __construct(
        public readonly string $account,
        public readonly string $pseudo,
        public readonly string $email,
        public readonly string $password,
        public readonly string $question,
        public readonly string $answer,
    ) {}
}

final class ValidationResult
{
    /** @param array<string,string> $errors */
    private function __construct(
        public readonly ?ValidatedAccount $account,
        public readonly array $errors,
    ) {}

    public static function success(ValidatedAccount $account): self
    {
        return new self($account, []);
    }

    /** @param array<string,string> $errors */
    public static function failure(array $errors): self
    {
        return new self(null, $errors);
    }

    public function isValid(): bool
    {
        return $this->account !== null;
    }
}

final class AccountValidator
{
    private const RESERVED = [
        'admin', 'administrator', 'root', 'system', 'moderator', 'moderateur',
        'starloco', 'ankama', 'support', 'staff', 'gm', 'mj',
    ];

    public function validate(AccountInput $input): ValidationResult
    {
        $errors = [];
        $submittedAccount = $input->account;
        $account = strtolower($input->account);
        $email = strtolower($input->email);
        $pseudo = preg_replace('/\s+/', ' ', $input->pseudo) ?? $input->pseudo;

        if (!preg_match('/\A[A-Za-z0-9][A-Za-z0-9_.-]{2,29}\z/', $submittedAccount)) {
            $errors['account'] = '3 à 30 caractères : lettres, chiffres, point, tiret ou underscore.';
        } elseif (in_array($account, self::RESERVED, true)) {
            $errors['account'] = 'Ce nom de compte est réservé.';
        }

        if (!preg_match('/\A[A-Za-z0-9][A-Za-z0-9 ._-]{2,29}\z/', $pseudo)) {
            $errors['pseudo'] = '3 à 30 caractères simples, sans symbole spécial.';
        } elseif (in_array(strtolower($pseudo), self::RESERVED, true)) {
            $errors['pseudo'] = 'Ce pseudo est réservé.';
        }

        if (strlen($email) > 100 || filter_var($email, FILTER_VALIDATE_EMAIL) === false) {
            $errors['email'] = 'Adresse e-mail invalide (100 caractères maximum).';
        }

        $passwordLength = $this->textLength($input->password);
        if ($passwordLength < 8 || $passwordLength > 32) {
            $errors['password'] = 'Le mot de passe doit contenir entre 8 et 32 caractères.';
        } elseif (!preg_match('/[A-Za-z]/', $input->password) || !preg_match('/[0-9]/', $input->password)) {
            $errors['password'] = 'Le mot de passe doit contenir au moins une lettre et un chiffre.';
        } elseif (!preg_match('/\A[\x{20}-\x{7E}\x{A0}-\x{FF}]+\z/u', $input->password)) {
            $errors['password'] = 'Le mot de passe contient un caractère incompatible avec le client Retro.';
        }

        if (!hash_equals($input->password, $input->passwordConfirmation)) {
            $errors['password_confirmation'] = 'Les deux mots de passe ne correspondent pas.';
        }

        if ($this->textLength($input->question) < 5 || $this->textLength($input->question) > 100) {
            $errors['question'] = 'La question secrète doit contenir entre 5 et 100 caractères.';
        } elseif ($this->containsControlCharacters($input->question)) {
            $errors['question'] = 'La question secrète contient un caractère interdit.';
        }

        if ($this->textLength($input->answer) < 2 || $this->textLength($input->answer) > 100) {
            $errors['answer'] = 'La réponse secrète doit contenir entre 2 et 100 caractères.';
        } elseif ($this->containsControlCharacters($input->answer)) {
            $errors['answer'] = 'La réponse secrète contient un caractère interdit.';
        }

        if ($errors !== []) {
            return ValidationResult::failure($errors);
        }

        return ValidationResult::success(new ValidatedAccount(
            account: $account,
            pseudo: $pseudo,
            email: $email,
            password: $input->password,
            question: $input->question,
            answer: $input->answer,
        ));
    }

    private function textLength(string $value): int
    {
        return function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
    }

    private function containsControlCharacters(string $value): bool
    {
        return preg_match('/[\x00-\x1F\x7F]/', $value) === 1;
    }
}

final class LegacyPasswordHasher
{
    public function hash(string $password): string
    {
        // Format attendu par StarLoco-Login : SHA-512 de la chaîne hexadécimale MD5.
        return hash('sha512', md5($password));
    }
}

final class AccountConflictException extends RuntimeException
{
    public function __construct(public readonly string $field)
    {
        parent::__construct('Un compte avec cette valeur existe déjà.');
    }
}

final class SqlConflictClassifier
{
    public static function field(PDOException $exception): string
    {
        $details = strtolower(implode(' ', array_filter([
            $exception->getMessage(),
            is_array($exception->errorInfo ?? null) ? (string) ($exception->errorInfo[2] ?? '') : '',
        ])));

        foreach ([
            'email' => ['uq_world_accounts_email', 'idx_world_accounts_email', "key 'email'", 'world_accounts.email'],
            'pseudo' => ['uq_world_accounts_pseudo', 'idx_world_accounts_pseudo', "key 'pseudo'", 'world_accounts.pseudo'],
            'account' => ['uq_world_accounts_account', "key 'account'", 'world_accounts.account'],
        ] as $field => $markers) {
            foreach ($markers as $marker) {
                if (str_contains($details, $marker)) {
                    return $field;
                }
            }
        }

        return 'account';
    }
}

interface AccountRepository
{
    /** @throws AccountConflictException */
    public function create(ValidatedAccount $account, string $passwordHash): int;
}

final class LazyAccountRepository implements AccountRepository
{
    private ?AccountRepository $delegate = null;

    /** @param Closure():PDO $pdoFactory */
    public function __construct(private readonly Closure $pdoFactory) {}

    public function create(ValidatedAccount $account, string $passwordHash): int
    {
        if ($this->delegate === null) {
            $factory = $this->pdoFactory;
            $this->delegate = new PdoAccountRepository($factory());
        }
        return $this->delegate->create($account, $passwordHash);
    }
}

final class PdoAccountRepository implements AccountRepository
{
    public function __construct(private readonly PDO $pdo) {}

    public function create(ValidatedAccount $account, string $passwordHash): int
    {
        $lockName = 'starloco.account.registration';
        $lock = $this->pdo->prepare('SELECT GET_LOCK(:name, 5)');
        $lock->execute(['name' => $lockName]);
        if ((int) $lock->fetchColumn() !== 1) {
            throw new RuntimeException('Le service d’inscription est occupé. Réessayez dans quelques secondes.');
        }

        try {
            if (!$this->pdo->inTransaction()) {
                $this->pdo->beginTransaction();
            }

            $conflict = $this->findConflict($account);
            if ($conflict !== null) {
                throw new AccountConflictException($conflict);
            }

            $statement = $this->pdo->prepare(
                'INSERT INTO world_accounts '
                . '(account, pass, email, question, reponse, pseudo, dateRegister, reload_needed, logged) '
                . 'VALUES (:account, :pass, :email, :question, :answer, :pseudo, :registered, 1, 0)'
            );
            $statement->execute([
                'account' => $account->account,
                'pass' => $passwordHash,
                'email' => $account->email,
                'question' => $account->question,
                'answer' => $account->answer,
                'pseudo' => $account->pseudo,
                // Compatible avec le varchar(10) du schéma historique.
                'registered' => date('Y-m-d'),
            ]);

            $id = (int) $this->pdo->lastInsertId();
            if ($this->pdo->inTransaction()) {
                $this->pdo->commit();
            }
            return $id;
        } catch (PDOException $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            if ((string) $exception->getCode() === '23000') {
                throw new AccountConflictException(SqlConflictClassifier::field($exception));
            }
            throw $exception;
        } catch (Throwable $exception) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $exception;
        } finally {
            try {
                $release = $this->pdo->prepare('SELECT RELEASE_LOCK(:name)');
                $release->execute(['name' => $lockName]);
            } catch (Throwable) {
                // La connexion libère aussi le verrou automatiquement.
            }
        }
    }

    private function findConflict(ValidatedAccount $account): ?string
    {
        $statement = $this->pdo->prepare(
            'SELECT account, email, pseudo FROM world_accounts '
            . 'WHERE account = :account OR email = :email OR pseudo = :pseudo LIMIT 1'
        );
        $statement->execute([
            'account' => $account->account,
            'email' => $account->email,
            'pseudo' => $account->pseudo,
        ]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        if (!is_array($row)) {
            return null;
        }
        if (isset($row['account']) && strcasecmp((string) $row['account'], $account->account) === 0) {
            return 'account';
        }
        if (isset($row['email']) && strcasecmp((string) $row['email'], $account->email) === 0) {
            return 'email';
        }
        return 'pseudo';
    }
}

final class AccountService
{
    public function __construct(
        private readonly AccountValidator $validator,
        private readonly LegacyPasswordHasher $hasher,
        private readonly AccountRepository $repository,
    ) {}

    /** @return array{ok:bool,id?:int,errors?:array<string,string>} */
    public function register(AccountInput $input): array
    {
        $validation = $this->validator->validate($input);
        if (!$validation->isValid() || $validation->account === null) {
            return ['ok' => false, 'errors' => $validation->errors];
        }

        try {
            $id = $this->repository->create(
                $validation->account,
                $this->hasher->hash($validation->account->password),
            );
            return ['ok' => true, 'id' => $id];
        } catch (AccountConflictException $exception) {
            $labels = [
                'account' => 'Ce nom de compte est déjà utilisé.',
                'email' => 'Cette adresse e-mail est déjà utilisée.',
                'pseudo' => 'Ce pseudo est déjà utilisé.',
            ];
            return ['ok' => false, 'errors' => [
                $exception->field => $labels[$exception->field] ?? 'Cette valeur est déjà utilisée.',
            ]];
        }
    }
}

function app_pdo(?AppConfig $config = null): PDO
{
    $config ??= AppConfig::fromEnvironment();
    if ($config->dbPassword === '') {
        throw new RuntimeException('Mot de passe MariaDB absent.');
    }

    $pdo = new PDO(
        sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $config->dbHost, $config->dbPort, $config->dbName),
        $config->dbUser,
        $config->dbPassword,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::ATTR_TIMEOUT => 5,
        ],
    );
    $pdo->exec("SET time_zone = '+00:00'");
    return $pdo;
}

function app_service(?PDO $pdo = null): AccountService
{
    $repository = $pdo !== null
        ? new PdoAccountRepository($pdo)
        : new LazyAccountRepository(static fn(): PDO => app_pdo());

    return new AccountService(new AccountValidator(), new LegacyPasswordHasher(), $repository);
}

function app_escape(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function app_start_session(): void
{
    if (PHP_SAPI === 'cli' || session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
    session_name('starloco_session');
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/',
        'secure' => $secure,
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
    session_start();
}

function app_csrf_token(): string
{
    app_start_session();
    if (!isset($_SESSION['csrf']) || !is_string($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function app_verify_csrf(string $token): bool
{
    app_start_session();
    return isset($_SESSION['csrf']) && is_string($_SESSION['csrf']) && hash_equals($_SESSION['csrf'], $token);
}

function app_rotate_csrf(): void
{
    app_start_session();
    $_SESSION['csrf'] = bin2hex(random_bytes(32));
}

function app_rate_limit_allows(int $maxAttempts = 5, int $windowSeconds = 900): bool
{
    app_start_session();
    $now = time();
    $attempts = array_values(array_filter(
        is_array($_SESSION['registration_attempts'] ?? null) ? $_SESSION['registration_attempts'] : [],
        static fn(mixed $timestamp): bool => is_int($timestamp) && $timestamp > $now - $windowSeconds,
    ));
    $_SESSION['registration_attempts'] = $attempts;
    return count($attempts) < $maxAttempts;
}

function app_record_attempt(): void
{
    app_start_session();
    $_SESSION['registration_attempts'] ??= [];
    $_SESSION['registration_attempts'][] = time();
}

function app_security_headers(): void
{
    if (headers_sent()) {
        return;
    }
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: same-origin');
    header("Content-Security-Policy: default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
    header('Cache-Control: no-store');
}
