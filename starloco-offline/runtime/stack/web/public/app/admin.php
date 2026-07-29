<?php
declare(strict_types=1);

require_once __DIR__ . '/social.php';

final class AdminConfig
{
    public function __construct(
        public readonly string $username,
        public readonly string $passwordHashFile,
        public readonly string $hmacKeyFile,
    ) {}

    public static function fromEnvironment(): self
    {
        return new self(
            username: (string) (getenv('ADMIN_USERNAME') ?: 'admin'),
            passwordHashFile: (string) (getenv('ADMIN_PASSWORD_HASH_FILE') ?: '/run/starloco/admin_password.hash'),
            hmacKeyFile: (string) (getenv('ADMIN_HMAC_KEY_FILE') ?: '/run/starloco/admin_hmac_key.secret'),
        );
    }

    public function passwordHash(): string
    {
        if (!is_readable($this->passwordHashFile)) {
            throw new RuntimeException('Secret administrateur indisponible.');
        }
        $hash = trim((string) file_get_contents($this->passwordHashFile));
        if (!str_starts_with($hash, '$argon2id$')) {
            throw new RuntimeException('Empreinte administrateur invalide.');
        }
        return $hash;
    }

    public function hmacKey(): string
    {
        if (!is_readable($this->hmacKeyFile)) {
            throw new RuntimeException('Clé d’audit indisponible.');
        }
        $key = trim((string) file_get_contents($this->hmacKeyFile));
        if (strlen($key) < 48) {
            throw new RuntimeException('Clé d’audit invalide.');
        }
        return $key;
    }
}

final class AdminOperationException extends RuntimeException {}
final class AdminRateLimitException extends RuntimeException {}

function admin_source_hash(?AdminConfig $config = null): string
{
    $config ??= AdminConfig::fromEnvironment();
    $remote = is_scalar($_SERVER['REMOTE_ADDR'] ?? null) ? (string) $_SERVER['REMOTE_ADDR'] : 'unknown';
    return hash_hmac('sha256', $remote, $config->hmacKey());
}

function admin_start_session(): void
{
    if (PHP_SAPI === 'cli' || session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
    session_name('starloco_admin');
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/admin',
        'secure' => $secure,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

function admin_login_allowed(PDO $pdo, string $sourceHash, int $maximum = 8, int $windowMinutes = 15): bool
{
    $maximum = max(1, min(50, $maximum));
    $windowMinutes = max(1, min(1440, $windowMinutes));
    $pdo->exec('DELETE FROM website_admin_login_attempts WHERE attempted_at < (NOW() - INTERVAL 1 DAY)');
    $statement = $pdo->prepare(
        'SELECT COUNT(*) FROM website_admin_login_attempts '
        . 'WHERE source_hash = :source_hash AND succeeded = 0 '
        . "AND attempted_at >= (NOW() - INTERVAL $windowMinutes MINUTE)"
    );
    $statement->execute(['source_hash' => $sourceHash]);
    return (int) $statement->fetchColumn() < $maximum;
}

function admin_record_login_attempt(PDO $pdo, string $sourceHash, bool $succeeded): void
{
    $statement = $pdo->prepare(
        'INSERT INTO website_admin_login_attempts (source_hash, succeeded) VALUES (:source_hash, :succeeded)'
    );
    $statement->execute([
        'source_hash' => $sourceHash,
        'succeeded' => $succeeded ? 1 : 0,
    ]);
}

function admin_authenticate(PDO $pdo, AdminConfig $config, string $username, string $password): bool
{
    $sourceHash = admin_source_hash($config);
    $lockName = 'starloco.admin.login.' . substr($sourceHash, 0, 32);
    $lock = $pdo->prepare('SELECT GET_LOCK(:name, 3)');
    $lock->execute(['name' => $lockName]);
    if ((int) $lock->fetchColumn() !== 1) {
        throw new AdminRateLimitException('Connexion occupée. Réessayez dans quelques secondes.');
    }

    try {
        if (!admin_login_allowed($pdo, $sourceHash)) {
            throw new AdminRateLimitException('Trop de tentatives. Réessayez dans quinze minutes.');
        }

        $expectedHash = $config->passwordHash();
        // Vérifier systématiquement Argon2id, même si l'identifiant est faux,
        // afin de ne pas créer de chemin de réponse nettement plus rapide.
        $passwordValid = password_verify($password, $expectedHash);
        $valid = hash_equals($config->username, $username) && $passwordValid;
        if ($valid) {
            $reset = $pdo->prepare(
                'DELETE FROM website_admin_login_attempts WHERE source_hash = :source_hash AND succeeded = 0'
            );
            $reset->execute(['source_hash' => $sourceHash]);
        }
        admin_record_login_attempt($pdo, $sourceHash, $valid);
    } finally {
        try {
            $release = $pdo->prepare('SELECT RELEASE_LOCK(:name)');
            $release->execute(['name' => $lockName]);
        } catch (Throwable) {
            // La connexion libère également le verrou nommé.
        }
    }

    if ($valid) {
        admin_start_session();
        session_regenerate_id(true);
        $now = time();
        $_SESSION['admin_authenticated'] = true;
        $_SESSION['admin_actor'] = $config->username;
        $_SESSION['admin_role'] = 'owner';
        $_SESSION['admin_authenticated_at'] = $now;
        $_SESSION['admin_last_seen'] = $now;
        $_SESSION['admin_secret_fingerprint'] = hash('sha256', $expectedHash);
        $_SESSION['admin_csrf'] = bin2hex(random_bytes(32));
    }
    return $valid;
}

function admin_is_authenticated(int $idleTimeout = 1800, int $absoluteTimeout = 28800): bool
{
    admin_start_session();
    $authenticated = ($_SESSION['admin_authenticated'] ?? false) === true;
    $lastSeen = is_int($_SESSION['admin_last_seen'] ?? null) ? $_SESSION['admin_last_seen'] : 0;
    $authenticatedAt = is_int($_SESSION['admin_authenticated_at'] ?? null)
        ? $_SESSION['admin_authenticated_at']
        : 0;
    $sessionFingerprint = is_string($_SESSION['admin_secret_fingerprint'] ?? null)
        ? $_SESSION['admin_secret_fingerprint']
        : '';
    try {
        $expectedFingerprint = hash('sha256', AdminConfig::fromEnvironment()->passwordHash());
    } catch (Throwable) {
        $expectedFingerprint = '';
    }
    if (
        !$authenticated
        || $lastSeen < time() - $idleTimeout
        || $authenticatedAt < time() - $absoluteTimeout
        || $sessionFingerprint === ''
        || !hash_equals($expectedFingerprint, $sessionFingerprint)
    ) {
        unset(
            $_SESSION['admin_authenticated'],
            $_SESSION['admin_actor'],
            $_SESSION['admin_role'],
            $_SESSION['admin_authenticated_at'],
            $_SESSION['admin_last_seen'],
            $_SESSION['admin_secret_fingerprint'],
        );
        return false;
    }
    $_SESSION['admin_last_seen'] = time();
    return true;
}

function admin_csrf_token(): string
{
    admin_start_session();
    if (!is_string($_SESSION['admin_csrf'] ?? null)) {
        $_SESSION['admin_csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['admin_csrf'];
}

function admin_verify_csrf(string $token): bool
{
    admin_start_session();
    return is_string($_SESSION['admin_csrf'] ?? null)
        && hash_equals($_SESSION['admin_csrf'], $token);
}

function admin_rotate_csrf(): void
{
    admin_start_session();
    $_SESSION['admin_csrf'] = bin2hex(random_bytes(32));
}

function admin_actor(): string
{
    admin_start_session();
    return is_string($_SESSION['admin_actor'] ?? null) ? $_SESSION['admin_actor'] : 'admin';
}

function admin_can(string $capability): bool
{
    admin_start_session();
    $role = is_string($_SESSION['admin_role'] ?? null) ? $_SESSION['admin_role'] : '';
    $capabilities = [
        'owner' => ['dashboard.view', 'players.view', 'players.edit', 'accounts.moderate', 'audit.view'],
    ];
    return in_array($capability, $capabilities[$role] ?? [], true);
}

function admin_require_capability(string $capability): void
{
    if (!admin_can($capability)) {
        http_response_code(403);
        throw new AdminOperationException('Permission administrateur insuffisante.');
    }
}

function admin_require_auth(bool $json = false): void
{
    if (admin_is_authenticated()) {
        return;
    }
    if ($json) {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code(401);
        echo json_encode(['ok' => false, 'error' => 'Session administrateur expirée.'], JSON_THROW_ON_ERROR);
    } else {
        header('Location: /admin/login', true, 303);
    }
    exit;
}

function admin_logout(): void
{
    admin_start_session();
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $parameters = session_get_cookie_params();
        setcookie(session_name(), '', [
            'expires' => time() - 42000,
            'path' => $parameters['path'],
            'domain' => $parameters['domain'],
            'secure' => $parameters['secure'],
            'httponly' => $parameters['httponly'],
            'samesite' => 'Strict',
        ]);
    }
    session_destroy();
}

function admin_set_flash(string $type, string $message): void
{
    admin_start_session();
    $_SESSION['admin_flash'] = ['type' => $type, 'message' => $message];
}

/** @return array{type:string,message:string}|null */
function admin_take_flash(): ?array
{
    admin_start_session();
    $flash = $_SESSION['admin_flash'] ?? null;
    unset($_SESSION['admin_flash']);
    if (!is_array($flash) || !is_string($flash['type'] ?? null) || !is_string($flash['message'] ?? null)) {
        return null;
    }
    return ['type' => $flash['type'], 'message' => $flash['message']];
}

function admin_integer(mixed $value, int $minimum, int $maximum, string $label): int
{
    if (!is_scalar($value) || !preg_match('/\A-?[0-9]+\z/', (string) $value)) {
        throw new AdminOperationException("$label doit être un nombre entier.");
    }
    $raw = (string) $value;
    $negative = str_starts_with($raw, '-');
    $digits = ltrim($negative ? substr($raw, 1) : $raw, '0');
    $digits = $digits === '' ? '0' : $digits;
    $normalized = $negative && $digits !== '0' ? '-' . $digits : $digits;
    $integer = filter_var($normalized, FILTER_VALIDATE_INT);
    if ($integer === false || $integer < $minimum || $integer > $maximum) {
        throw new AdminOperationException("$label doit être compris entre $minimum et $maximum.");
    }
    return (int) $integer;
}

function admin_item_type_label(int $type): string
{
    return [
        1 => 'Amulette', 2 => 'Arc', 3 => 'Baguette', 4 => 'Bâton', 5 => 'Dagues',
        6 => 'Épée', 7 => 'Marteau', 8 => 'Pelle', 9 => 'Anneau', 10 => 'Ceinture',
        11 => 'Bottes', 12 => 'Potion', 13 => 'Parchemin', 15 => 'Ressource',
        16 => 'Coiffe', 17 => 'Cape', 18 => 'Familier', 19 => 'Hache',
        20 => 'Outil', 22 => 'Dofus', 23 => 'Objet de quête', 24 => 'Objet de quête',
        26 => 'Document', 33 => 'Bouclier', 41 => 'Objet de monture',
        77 => 'Certificat de familier', 83 => 'Pierre d’âme',
        85 => 'Pierre d’âme pleine', 93 => 'Objet d’élevage', 97 => 'Certificat de monture',
    ][$type] ?? "Type $type";
}

/** @return array{supported:bool,reason:?string} */
function admin_item_support(int $templateId, int $type): array
{
    $specialTemplates = [
        6653, 8378, 9544, 9545, 9546, 9547, 9548,
        10125, 10126, 10127, 10133,
        10289, 10290, 10291, 10292, 10293, 10294,
        10295, 10296, 10297, 10298, 10299, 10300,
    ];
    $specialTypes = [18, 24, 77, 85, 90, 91, 93, 97, 113, 115];
    if (in_array($type, $specialTypes, true) || in_array($templateId, $specialTemplates, true)) {
        return [
            'supported' => false,
            'reason' => 'Cet objet possède des données annexes que seul le serveur de jeu peut créer.',
        ];
    }
    return ['supported' => true, 'reason' => null];
}

function admin_item_stats(string $template, string $quality): string
{
    if ($quality !== 'perfect' || $template === '') {
        return $template;
    }

    $weaponEffects = array_flip([91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 108, 110, 139, 605, 614, 615, 812]);
    $result = [];
    foreach (explode(',', $template) as $stat) {
        $parts = explode('#', $stat);
        if (count($parts) < 5 || !ctype_xdigit($parts[0])) {
            $result[] = $stat;
            continue;
        }
        $id = hexdec($parts[0]);
        $isText = ($parts[3] ?? '') !== '' && ($parts[3] ?? '') !== '0';
        if (
            !isset($weaponEffects[$id])
            && !($id >= 281 && $id <= 294)
            && !$isText
            && ($parts[2] ?? '') !== ''
            && ctype_xdigit($parts[2])
            && hexdec($parts[2]) > 0
        ) {
            $parts[1] = $parts[2];
        }
        $result[] = implode('#', $parts);
    }
    return implode(',', $result);
}

final class AdminConsole
{
    private const MAX_KAMAS = 1000000000;
    private const MAX_STAT = 1000000;

    public function __construct(
        private readonly PDO $pdo,
        private readonly string $actor,
        private readonly string $sourceHash,
        private readonly string $requestId,
    ) {}

    /** @return array<string,int> */
    public function stats(): array
    {
        $players = $this->pdo->query(
            'SELECT COUNT(*) AS characters, COALESCE(SUM(logged = 1), 0) AS online, '
            . 'COALESCE(SUM(kamas), 0) AS total_kamas FROM world_players'
        )->fetch(PDO::FETCH_ASSOC);
        $accounts = $this->pdo->query(
            'SELECT COUNT(*) AS accounts, COALESCE(SUM(banned = 1), 0) AS banned FROM world_accounts'
        )->fetch(PDO::FETCH_ASSOC);
        return [
            'characters' => (int) ($players['characters'] ?? 0),
            'online' => (int) ($players['online'] ?? 0),
            'totalKamas' => (int) ($players['total_kamas'] ?? 0),
            'accounts' => (int) ($accounts['accounts'] ?? 0),
            'banned' => (int) ($accounts['banned'] ?? 0),
        ];
    }

    /** @return list<array<string,mixed>> */
    public function findPlayers(string $query = '', int $limit = 30): array
    {
        $query = trim(mb_substr($query, 0, 50));
        $conditions = '';
        $params = [];
        if ($query !== '') {
            $conditions = 'WHERE p.name LIKE :player_query OR a.account LIKE :account_query OR a.pseudo LIKE :pseudo_query';
            $like = '%' . $query . '%';
            $params = ['player_query' => $like, 'account_query' => $like, 'pseudo_query' => $like];
        }
        $limit = max(1, min(100, $limit));
        $statement = $this->pdo->prepare(
            'SELECT p.id, p.name, p.class AS class_id, p.level, p.xp, p.kamas, p.logged, '
            . 'a.account AS account_name, a.pseudo, a.banned, a.logged AS account_logged, '
            . 'g.name AS guild_name '
            . 'FROM world_players p '
            . 'INNER JOIN world_accounts a ON a.guid = p.account '
            . 'LEFT JOIN starloco_game.guild_members gm ON gm.guid = p.id '
            . 'LEFT JOIN world_guilds g ON g.id = gm.guild '
            . "$conditions ORDER BY p.logged DESC, p.level DESC, p.xp DESC, p.id ASC LIMIT $limit"
        );
        $statement->execute($params);
        return $this->decoratePlayers($statement->fetchAll(PDO::FETCH_ASSOC));
    }

    /** @return array<string,mixed>|null */
    public function player(int $id): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT p.*, a.account AS account_name, a.pseudo, a.email, a.banned, '
            . 'a.logged AS account_logged, a.lastConnectionDate, a.dateRegister, '
            . 'g.name AS guild_name, gm.rank AS guild_rank '
            . 'FROM world_players p '
            . 'INNER JOIN world_accounts a ON a.guid = p.account '
            . 'LEFT JOIN starloco_game.guild_members gm ON gm.guid = p.id '
            . 'LEFT JOIN world_guilds g ON g.id = gm.guild '
            . 'WHERE p.id = :id LIMIT 1'
        );
        $statement->execute(['id' => $id]);
        $row = $statement->fetch(PDO::FETCH_ASSOC);
        if (!is_array($row)) {
            return null;
        }
        $class = social_class_meta((int) $row['class']);
        $row['className'] = $class['name'];
        $row['classGlyph'] = $class['glyph'];
        $row['online'] = (int) $row['logged'] === 1 || (int) $row['account_logged'] === 1;
        $row['editable'] = !$row['online'];
        return $row;
    }

    /** @param array<string,mixed> $player @return list<array<string,mixed>> */
    public function inventory(array $player): array
    {
        $ids = social_parse_object_ids((string) ($player['objets'] ?? ''));
        if ($ids === []) {
            return [];
        }
        $placeholders = implode(',', array_fill(0, count($ids), '?'));
        $statement = $this->pdo->prepare(
            'SELECT o.id, o.template AS template_id, o.quantity, o.position, o.stats, o.puit, '
            . 't.name, t.type, t.level, t.pod, t.avgPrice '
            . 'FROM world_objects o '
            . 'LEFT JOIN starloco_game.item_template t ON t.id = o.template '
            . "WHERE o.id IN ($placeholders) "
            . 'ORDER BY (o.position >= 0) DESC, t.level DESC, t.name ASC, o.id ASC'
        );
        $statement->execute($ids);
        $items = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $row['typeName'] = admin_item_type_label((int) ($row['type'] ?? -1));
            $items[] = $row;
        }
        return $items;
    }

    /** @return list<array<string,mixed>> */
    public function searchItems(string $query, int $limit = 24): array
    {
        $query = trim(mb_substr($query, 0, 60));
        if ($query === '') {
            return [];
        }
        $limit = max(1, min(40, $limit));
        $numericId = ctype_digit($query) ? (int) $query : -1;
        $statement = $this->pdo->prepare(
            'SELECT id, name, type, level, pod, avgPrice, statsTemplate '
            . 'FROM starloco_game.item_template '
            . 'WHERE id = :template_id OR name LIKE :query '
            . "ORDER BY (id = :order_id) DESC, level DESC, name ASC LIMIT $limit"
        );
        $statement->execute([
            'template_id' => $numericId,
            'query' => '%' . $query . '%',
            'order_id' => $numericId,
        ]);
        $items = [];
        foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
            $support = admin_item_support((int) $row['id'], (int) $row['type']);
            $items[] = [
                'id' => (int) $row['id'],
                'name' => (string) $row['name'],
                'type' => (int) $row['type'],
                'typeName' => admin_item_type_label((int) $row['type']),
                'level' => (int) $row['level'],
                'pod' => (int) $row['pod'],
                'avgPrice' => (int) $row['avgPrice'],
                'supported' => $support['supported'],
                'reason' => $support['reason'],
            ];
        }
        return $items;
    }

    /** @return list<array<string,mixed>> */
    public function recentAudit(int $limit = 20, ?int $playerId = null): array
    {
        $limit = max(1, min(100, $limit));
        if ($playerId !== null) {
            $statement = $this->pdo->prepare(
                "SELECT * FROM website_admin_audit WHERE player_id = :player_id ORDER BY id DESC LIMIT $limit"
            );
            $statement->execute(['player_id' => $playerId]);
            return $statement->fetchAll(PDO::FETCH_ASSOC);
        }
        return $this->pdo->query(
            "SELECT * FROM website_admin_audit ORDER BY id DESC LIMIT $limit"
        )->fetchAll(PDO::FETCH_ASSOC);
    }

    public function recordFailure(string $action, int $playerId, string $summary): void
    {
        $allowedActions = [
            'set_kamas', 'adjust_kamas', 'set_resources', 'give_item',
            'remove_item', 'ban_account', 'unban_account', 'unknown',
        ];
        if (!in_array($action, $allowedActions, true)) {
            $action = 'unknown';
        }
        $playerName = null;
        if ($playerId > 0) {
            $statement = $this->pdo->prepare('SELECT name FROM world_players WHERE id = :id LIMIT 1');
            $statement->execute(['id' => $playerId]);
            $name = $statement->fetchColumn();
            $playerName = is_string($name) ? $name : null;
        }
        $statement = $this->pdo->prepare(
            'INSERT INTO website_admin_audit '
            . '(request_id, actor, role, action, outcome, player_id, player_name, summary, payload_json, source_hash) '
            . 'VALUES (:request_id, :actor, :role, :action, :outcome, :player_id, :player_name, '
            . ':summary, :payload_json, :source_hash)'
        );
        $statement->execute([
            'request_id' => $this->requestId,
            'actor' => $this->actor,
            'role' => 'owner',
            'action' => $action,
            'outcome' => 'failure',
            'player_id' => $playerId > 0 ? $playerId : null,
            'player_name' => $playerName,
            'summary' => mb_substr($summary, 0, 190),
            'payload_json' => json_encode(['reason' => mb_substr($summary, 0, 190)], JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE),
            'source_hash' => $this->sourceHash,
        ]);
    }

    public function setKamas(int $playerId, int $amount): string
    {
        if ($amount < 0 || $amount > self::MAX_KAMAS) {
            throw new AdminOperationException('Le montant de kamas est hors limites.');
        }
        return $this->withPlayerLock($playerId, function (array $player) use ($amount): string {
            $before = (int) $player['kamas'];
            $statement = $this->pdo->prepare('UPDATE world_players SET kamas = :kamas WHERE id = :id AND logged = 0');
            $statement->execute(['kamas' => $amount, 'id' => (int) $player['id']]);
            if ($before !== $amount && $statement->rowCount() !== 1) {
                throw new AdminOperationException('Le personnage a changé d’état pendant l’opération.');
            }
            $this->markAccountForReload((int) $player['account']);
            $this->audit('set_kamas', $player, "Kamas : $before → $amount", [
                'before' => $before,
                'after' => $amount,
            ]);
            return 'Le portefeuille a été mis à jour.';
        });
    }

    public function adjustKamas(int $playerId, int $delta): string
    {
        if ($delta < -self::MAX_KAMAS || $delta > self::MAX_KAMAS) {
            throw new AdminOperationException('L’ajustement de kamas est hors limites.');
        }
        return $this->withPlayerLock($playerId, function (array $player) use ($delta): string {
            $before = (int) $player['kamas'];
            $after = $before + $delta;
            if ($after < 0 || $after > self::MAX_KAMAS) {
                throw new AdminOperationException('Ce changement placerait le portefeuille hors limites.');
            }
            $statement = $this->pdo->prepare('UPDATE world_players SET kamas = :kamas WHERE id = :id AND logged = 0');
            $statement->execute(['kamas' => $after, 'id' => (int) $player['id']]);
            if ($before !== $after && $statement->rowCount() !== 1) {
                throw new AdminOperationException('Le personnage a changé d’état pendant l’opération.');
            }
            $this->markAccountForReload((int) $player['account']);
            $this->audit('adjust_kamas', $player, sprintf('Kamas : %d %s%d = %d', $before, $delta >= 0 ? '+' : '', $delta, $after), [
                'before' => $before,
                'delta' => $delta,
                'after' => $after,
            ]);
            return 'Les kamas ont été ajustés.';
        });
    }

    /** @param array<string,int> $values */
    public function setResources(int $playerId, array $values): string
    {
        foreach (['capital', 'spellboost', 'vitalite', 'force', 'sagesse', 'intelligence', 'chance', 'agilite'] as $field) {
            if (!isset($values[$field]) || $values[$field] < 0 || $values[$field] > self::MAX_STAT) {
                throw new AdminOperationException("Valeur invalide pour $field.");
            }
        }
        if (!isset($values['energy']) || $values['energy'] < 0 || $values['energy'] > 10000) {
            throw new AdminOperationException('L’énergie doit être comprise entre 0 et 10000.');
        }

        return $this->withPlayerLock($playerId, function (array $player) use ($values): string {
            $before = [];
            foreach (array_keys($values) as $field) {
                $before[$field] = (int) $player[$field];
            }
            $statement = $this->pdo->prepare(
                'UPDATE world_players SET capital=:capital, spellboost=:spellboost, energy=:energy, '
                . 'vitalite=:vitalite, `force`=:force_value, sagesse=:sagesse, intelligence=:intelligence, '
                . 'chance=:chance, agilite=:agilite WHERE id=:id AND logged=0'
            );
            $statement->execute([
                'capital' => $values['capital'],
                'spellboost' => $values['spellboost'],
                'energy' => $values['energy'],
                'vitalite' => $values['vitalite'],
                'force_value' => $values['force'],
                'sagesse' => $values['sagesse'],
                'intelligence' => $values['intelligence'],
                'chance' => $values['chance'],
                'agilite' => $values['agilite'],
                'id' => (int) $player['id'],
            ]);
            $changed = false;
            foreach ($values as $field => $value) {
                if ((int) $player[$field] !== $value) {
                    $changed = true;
                    break;
                }
            }
            if ($changed && $statement->rowCount() !== 1) {
                throw new AdminOperationException('Le personnage a changé d’état pendant l’opération.');
            }
            $this->markAccountForReload((int) $player['account']);
            $this->audit('set_resources', $player, 'Caractéristiques et points mis à jour', [
                'before' => $before,
                'after' => $values,
            ]);
            return 'Les caractéristiques ont été mises à jour.';
        });
    }

    public function giveItem(int $playerId, int $templateId, int $quantity, string $quality): string
    {
        if ($quantity < 1 || $quantity > 99999) {
            throw new AdminOperationException('La quantité doit être comprise entre 1 et 99999.');
        }
        if (!in_array($quality, ['base', 'perfect'], true)) {
            throw new AdminOperationException('Qualité d’objet invalide.');
        }

        return $this->withPlayerLock($playerId, function (array $player) use ($templateId, $quantity, $quality): string {
            $templateStatement = $this->pdo->prepare(
                'SELECT id, name, type, level, statsTemplate FROM starloco_game.item_template WHERE id = :id LIMIT 1'
            );
            $templateStatement->execute(['id' => $templateId]);
            $template = $templateStatement->fetch(PDO::FETCH_ASSOC);
            if (!is_array($template)) {
                throw new AdminOperationException('Template d’objet introuvable.');
            }
            $support = admin_item_support((int) $template['id'], (int) $template['type']);
            if (!$support['supported']) {
                throw new AdminOperationException((string) $support['reason']);
            }

            $stats = admin_item_stats((string) $template['statsTemplate'], $quality);
            $insert = $this->pdo->prepare(
                'INSERT INTO world_objects (template, quantity, position, stats, puit) '
                . 'VALUES (:template, :quantity, -1, :stats, 0)'
            );
            $insert->execute([
                'template' => (int) $template['id'],
                'quantity' => $quantity,
                'stats' => $stats,
            ]);
            $objectId = (int) $this->pdo->lastInsertId();
            if ($objectId <= 0) {
                throw new AdminOperationException('Le serveur n’a pas attribué d’identifiant à l’objet.');
            }

            try {
                $ids = social_parse_object_ids((string) $player['objets']);
                $ids[] = $objectId;
                $encoded = implode('|', array_values(array_unique($ids))) . '|';
                $update = $this->pdo->prepare(
                    'UPDATE world_players SET objets = :objects WHERE id = :id AND logged = 0'
                );
                $update->execute(['objects' => $encoded, 'id' => (int) $player['id']]);
                if ($update->rowCount() !== 1) {
                    throw new AdminOperationException('Le personnage a changé d’état pendant l’opération.');
                }
            } catch (Throwable $exception) {
                $cleanup = $this->pdo->prepare('DELETE FROM world_objects WHERE id = :id');
                $cleanup->execute(['id' => $objectId]);
                throw $exception;
            }

            $this->markAccountForReload((int) $player['account']);
            $this->audit('give_item', $player, sprintf('%s × %d ajouté', (string) $template['name'], $quantity), [
                'objectId' => $objectId,
                'templateId' => (int) $template['id'],
                'templateName' => (string) $template['name'],
                'quantity' => $quantity,
                'quality' => $quality,
            ]);
            return sprintf('%s × %d a été ajouté à l’inventaire.', (string) $template['name'], $quantity);
        });
    }

    public function removeItem(int $playerId, int $objectId, int $quantity): string
    {
        if ($quantity < 1 || $quantity > 99999) {
            throw new AdminOperationException('La quantité à retirer est invalide.');
        }

        return $this->withPlayerLock($playerId, function (array $player) use ($objectId, $quantity): string {
            $ids = social_parse_object_ids((string) $player['objets']);
            if (!in_array($objectId, $ids, true)) {
                throw new AdminOperationException('Cet objet n’appartient pas au personnage.');
            }
            $statement = $this->pdo->prepare(
                'SELECT o.id, o.template, o.quantity, t.name, t.type '
                . 'FROM world_objects o LEFT JOIN starloco_game.item_template t ON t.id = o.template '
                . 'WHERE o.id = :id LIMIT 1 FOR UPDATE'
            );
            $statement->execute(['id' => $objectId]);
            $object = $statement->fetch(PDO::FETCH_ASSOC);
            if (!is_array($object)) {
                throw new AdminOperationException('L’objet référencé est introuvable.');
            }
            if ($object['type'] === null) {
                throw new AdminOperationException('Le template de cet objet est absent.');
            }
            $support = admin_item_support((int) $object['template'], (int) $object['type']);
            if (!$support['supported']) {
                throw new AdminOperationException('Cet objet spécial doit être géré depuis le serveur de jeu.');
            }

            $before = (int) $object['quantity'];
            $removed = min($quantity, $before);
            $after = $before - $removed;
            if ($after > 0) {
                $update = $this->pdo->prepare('UPDATE world_objects SET quantity = :quantity WHERE id = :id');
                $update->execute(['quantity' => $after, 'id' => $objectId]);
            } else {
                $remaining = array_values(array_filter($ids, static fn(int $id): bool => $id !== $objectId));
                $encoded = $remaining === [] ? '' : implode('|', $remaining) . '|';
                $updatePlayer = $this->pdo->prepare(
                    'UPDATE world_players SET objets = :objects WHERE id = :id AND logged = 0'
                );
                $updatePlayer->execute(['objects' => $encoded, 'id' => (int) $player['id']]);
                if ($updatePlayer->rowCount() !== 1) {
                    throw new AdminOperationException('Le personnage a changé d’état pendant l’opération.');
                }
                $delete = $this->pdo->prepare('DELETE FROM world_objects WHERE id = :id');
                $delete->execute(['id' => $objectId]);
            }

            $this->markAccountForReload((int) $player['account']);
            $name = trim((string) ($object['name'] ?? '')) ?: 'Objet #' . (int) $object['template'];
            $this->audit('remove_item', $player, sprintf('%s × %d retiré', $name, $removed), [
                'objectId' => $objectId,
                'templateId' => (int) $object['template'],
                'templateName' => $name,
                'before' => $before,
                'removed' => $removed,
                'after' => $after,
            ]);
            return sprintf('%s × %d a été retiré.', $name, $removed);
        });
    }

    public function setBan(int $playerId, bool $banned): string
    {
        return $this->withPlayerLock($playerId, function (array $player) use ($banned): string {
            $statement = $this->pdo->prepare(
                'UPDATE world_accounts SET banned = :banned, bannedTime = 0, reload_needed = 1 WHERE guid = :id'
            );
            $statement->execute(['banned' => $banned ? 1 : 0, 'id' => (int) $player['account']]);
            $this->audit($banned ? 'ban_account' : 'unban_account', $player, $banned ? 'Compte banni' : 'Compte débanni', [
                'banned' => $banned,
            ]);
            return $banned ? 'Le compte a été banni.' : 'Le compte a été débanni.';
        });
    }

    /**
     * @template T
     * @param callable(array<string,mixed>):T $callback
     * @return T
     */
    private function withPlayerLock(int $playerId, callable $callback): mixed
    {
        if ($playerId <= 0) {
            throw new AdminOperationException('Personnage invalide.');
        }
        $lockName = 'starloco.admin.player.' . $playerId;
        $lock = $this->pdo->prepare('SELECT GET_LOCK(:name, 5)');
        $lock->execute(['name' => $lockName]);
        if ((int) $lock->fetchColumn() !== 1) {
            throw new AdminOperationException('Ce personnage est déjà en cours de modification.');
        }

        try {
            if (!$this->pdo->inTransaction()) {
                $this->pdo->beginTransaction();
            }
            $player = $this->playerForMutation($playerId);
            if ($player === null) {
                throw new AdminOperationException('Personnage introuvable.');
            }
            if ($player['online']) {
                throw new AdminOperationException('Déconnectez ce personnage avant de le modifier.');
            }
            $result = $callback($player);
            if ($this->pdo->inTransaction()) {
                $this->pdo->commit();
            }
            return $result;
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
                // La fermeture de la connexion libère également le verrou.
            }
        }
    }

    /** @return array<string,mixed>|null */
    private function playerForMutation(int $playerId): ?array
    {
        $statement = $this->pdo->prepare(
            'SELECT p.*, a.account AS account_name, a.pseudo, a.banned, '
            . 'a.logged AS account_logged '
            . 'FROM world_players p '
            . 'INNER JOIN world_accounts a ON a.guid = p.account '
            . 'WHERE p.id = :id LIMIT 1 FOR UPDATE'
        );
        $statement->execute(['id' => $playerId]);
        $player = $statement->fetch(PDO::FETCH_ASSOC);
        if (!is_array($player)) {
            return null;
        }
        $class = social_class_meta((int) $player['class']);
        $player['className'] = $class['name'];
        $player['classGlyph'] = $class['glyph'];
        $player['online'] = (int) $player['logged'] === 1 || (int) $player['account_logged'] === 1;
        return $player;
    }

    /** @param list<array<string,mixed>> $rows @return list<array<string,mixed>> */
    private function decoratePlayers(array $rows): array
    {
        $players = [];
        foreach ($rows as $row) {
            $class = social_class_meta((int) $row['class_id']);
            $row['className'] = $class['name'];
            $row['classGlyph'] = $class['glyph'];
            $row['online'] = (int) $row['logged'] === 1 || (int) $row['account_logged'] === 1;
            $players[] = $row;
        }
        return $players;
    }

    private function markAccountForReload(int $accountId): void
    {
        $statement = $this->pdo->prepare('UPDATE world_accounts SET reload_needed = 1 WHERE guid = :id');
        $statement->execute(['id' => $accountId]);
    }

    /** @param array<string,mixed> $player @param array<string,mixed> $payload */
    private function audit(string $action, array $player, string $summary, array $payload): void
    {
        $statement = $this->pdo->prepare(
            'INSERT INTO website_admin_audit '
            . '(request_id, actor, role, action, outcome, player_id, player_name, summary, payload_json, source_hash) '
            . 'VALUES (:request_id, :actor, :role, :action, :outcome, :player_id, :player_name, '
            . ':summary, :payload_json, :source_hash)'
        );
        $statement->execute([
            'request_id' => $this->requestId,
            'actor' => $this->actor,
            'role' => 'owner',
            'action' => $action,
            'outcome' => 'success',
            'player_id' => (int) $player['id'],
            'player_name' => (string) $player['name'],
            'summary' => mb_substr($summary, 0, 190),
            'payload_json' => json_encode($payload, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            'source_hash' => $this->sourceHash,
        ]);
    }
}

function admin_console(?PDO $pdo = null): AdminConsole
{
    return new AdminConsole(
        $pdo ?? app_pdo(),
        admin_actor(),
        admin_source_hash(),
        bin2hex(random_bytes(16)),
    );
}
