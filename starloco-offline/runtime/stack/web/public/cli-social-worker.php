<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/app/social.php';

set_time_limit(0);
$config = SocialConfig::fromEnvironment();
$lastConnectionAttempt = 0;
$pdo = null;

fwrite(STDOUT, "[social-worker] démarrage, intervalle {$config->scanIntervalSeconds}s\n");

while (true) {
    try {
        if (!$pdo instanceof PDO) {
            $now = time();
            if ($now - $lastConnectionAttempt < 3) {
                sleep(1);
                continue;
            }
            $lastConnectionAttempt = $now;
            $pdo = app_pdo();
        }

        $result = social_worker_tick($pdo, $config);
        if ($result['created_events'] > 0) {
            fwrite(STDOUT, sprintf(
                "[social-worker] %d personnage(s), %d événement(s) créé(s)\n",
                $result['scanned_players'],
                $result['created_events'],
            ));
        }
    } catch (Throwable $exception) {
        fwrite(STDERR, '[social-worker] ' . $exception->getMessage() . "\n");
        $pdo = null;
    }

    sleep($config->scanIntervalSeconds);
}
