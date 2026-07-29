<?php
declare(strict_types=1);
require_once __DIR__ . '/app/bootstrap.php';

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

function prompt(string $label, bool $secret = false): string
{
    fwrite(STDOUT, $label . ': ');
    $canHide = $secret
        && PHP_OS_FAMILY !== 'Windows'
        && function_exists('stream_isatty')
        && stream_isatty(STDIN);

    try {
        if ($canHide) {
            system('stty -echo');
        }
        $line = fgets(STDIN);
        if ($line === false) {
            throw new RuntimeException('Entrée interrompue.');
        }
        return trim($line);
    } finally {
        if ($canHide) {
            system('stty echo');
            fwrite(STDOUT, PHP_EOL);
        }
    }
}

$account = prompt('Nom de compte');
$pseudo = prompt('Pseudo public');
$email = prompt('E-mail');
$password = prompt('Mot de passe', true);
$confirmation = prompt('Confirmation du mot de passe', true);
$question = prompt('Question secrète');
$answer = prompt('Réponse secrète', true);

try {
    $result = app_service()->register(new AccountInput($account, $pseudo, $email, $password, $confirmation, $question, $answer));
    if (!$result['ok']) {
        foreach ($result['errors'] ?? [] as $field => $message) {
            fwrite(STDERR, sprintf("%s: %s\n", $field, $message));
        }
        exit(2);
    }
    fwrite(STDOUT, "Compte créé avec l’identifiant interne " . (int) $result['id'] . ".\n");
} catch (Throwable $exception) {
    fwrite(STDERR, "Création impossible: " . $exception->getMessage() . "\n");
    exit(1);
}
