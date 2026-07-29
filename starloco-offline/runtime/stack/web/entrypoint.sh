#!/bin/sh
set -eu

copy_secret() {
  source_file="$1"
  target_file="$2"

  if [ ! -r "$source_file" ]; then
    echo "[web-entrypoint] Secret source illisible : $source_file" >&2
    exit 1
  fi

  mkdir -p "$(dirname "$target_file")"
  umask 077
  cat "$source_file" > "$target_file"

  if id www-data >/dev/null 2>&1; then
    chown "$(id -u www-data):$(id -g www-data)" "$target_file"
  else
    echo "[web-entrypoint] Utilisateur www-data introuvable." >&2
    exit 1
  fi
  chmod 0400 "$target_file"
}

copy_secret \
  "${STARLOCO_DB_PASSWORD_SOURCE_FILE:-/run/secrets/starloco_db_password_secret}" \
  "${STARLOCO_DB_PASSWORD_FILE:-/run/starloco/starloco_db_password.secret}"
admin_password_source="${ADMIN_PASSWORD_SOURCE_FILE:-/run/secrets/admin_password_secret}"
admin_password_hash="${ADMIN_PASSWORD_HASH_FILE:-/run/starloco/admin_password.hash}"
if [ ! -r "$admin_password_source" ]; then
  echo "[web-entrypoint] Secret administrateur illisible." >&2
  exit 1
fi
mkdir -p "$(dirname "$admin_password_hash")"
php -r '
  $password = trim((string) file_get_contents($argv[1]));
  if (strlen($password) < 24 || !defined("PASSWORD_ARGON2ID")) {
      fwrite(STDERR, "Secret administrateur ou Argon2id indisponible.\n");
      exit(1);
  }
  $hash = password_hash($password, PASSWORD_ARGON2ID);
  if (!is_string($hash)) {
      exit(1);
  }
  echo $hash;
' "$admin_password_source" > "$admin_password_hash"
chown "$(id -u www-data):$(id -g www-data)" "$admin_password_hash"
chmod 0400 "$admin_password_hash"

copy_secret \
  "${ADMIN_HMAC_KEY_SOURCE_FILE:-/run/secrets/admin_hmac_key_secret}" \
  "${ADMIN_HMAC_KEY_FILE:-/run/starloco/admin_hmac_key.secret}"

exec "$@"
