#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$ROOT/offline-common.sh"

HOST=127.0.0.1
LOGIN_PORT=450
GAME_PORT=5555
SERVER_ID=601
TIMEOUT=8
KEEP_ACCOUNT=0
SKIP_VERSION_REJECTION=0
QUIET=0

usage() {
  cat <<'EOF'
Usage : ./offline-smoke-test.sh [options]

Teste de bout en bout le protocole Dofus Retro 1.41.9e :
  login TCP 450 -> monde 601 -> création/sélection d'un personnage ->
  inventaire/sorts -> chargement de carte -> reconnexion -> suppression.

Le script crée un compte aléatoire avec le service d'inscription du portail.
Par défaut, il le supprime ensuite, même si le test échoue. La suppression est
limitée au compte créé et refusée si le personnage temporaire, ou tout autre
personnage, subsiste.

Options :
  --host HOTE                 Hôte publié par Docker (défaut : 127.0.0.1)
  --login-port PORT           Port login (défaut : 450)
  --game-port PORT            Port jeu (défaut : 5555)
  --server-id ID              Identifiant du monde (défaut : 601)
  --timeout SECONDES          Délai par étape (défaut : 8)
  --skip-version-rejection    Ne teste pas le refus de la révision précédente
  --keep-account              Conserve le compte temporaire pour débogage
  --quiet                     Masque le détail des trames
  -h, --help                  Affiche cette aide
EOF
}

require_value() {
  local option="$1" value="${2:-}"
  [[ -n "$value" ]] || die "Valeur absente pour $option."
}

while (($# > 0)); do
  case "$1" in
    --host)
      require_value "$1" "${2:-}"
      HOST="$2"
      shift 2
      ;;
    --login-port)
      require_value "$1" "${2:-}"
      LOGIN_PORT="$2"
      shift 2
      ;;
    --game-port)
      require_value "$1" "${2:-}"
      GAME_PORT="$2"
      shift 2
      ;;
    --server-id)
      require_value "$1" "${2:-}"
      SERVER_ID="$2"
      shift 2
      ;;
    --timeout)
      require_value "$1" "${2:-}"
      TIMEOUT="$2"
      shift 2
      ;;
    --skip-version-rejection)
      SKIP_VERSION_REJECTION=1
      shift
      ;;
    --keep-account)
      KEEP_ACCOUNT=1
      shift
      ;;
    --quiet)
      QUIET=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Option inconnue : $1 (essayez --help)."
      ;;
  esac
done

[[ "$LOGIN_PORT" =~ ^[0-9]+$ ]] || die "--login-port doit être numérique."
[[ "$GAME_PORT" =~ ^[0-9]+$ ]] || die "--game-port doit être numérique."
[[ "$SERVER_ID" =~ ^[0-9]+$ ]] || die "--server-id doit être numérique."
[[ "$TIMEOUT" =~ ^[0-9]+([.][0-9]+)?$ ]] || die "--timeout doit être numérique."
(( LOGIN_PORT >= 1 && LOGIN_PORT <= 65535 )) || die "--login-port hors limites."
(( GAME_PORT >= 1 && GAME_PORT <= 65535 )) || die "--game-port hors limites."
(( SERVER_ID > 0 )) || die "--server-id doit être strictement positif."

command -v python3 >/dev/null 2>&1 || die "Python 3 est absent."
need_docker

running_services="$(compose ps --services --status running)"
for service in mariadb web login game; do
  grep -qx "$service" <<<"$running_services" ||
    die "Le service Docker '$service' n'est pas démarré. Lancez ./offline-start.sh."
done
wait_for_service_health login 1 || die "Le service login n'est pas sain."
wait_for_service_health game 1 || die "Le service jeu n'est pas sain."
wait_for_service_health web 1 || die "Le portail n'est pas sain."

suffix="$(date +%s)$(random_hex 3)"
account="smoke_1419${suffix}"
account="${account:0:30}"
pseudo="Smoke_${suffix}"
pseudo="${pseudo:0:30}"
character_token="$(random_hex 3 | tr '0-9' 'g-p' | sed 's/./x&/g')"
character="Smoke-${character_token}"
email="${account}@invalid.local"
password="Retro1419$(random_hex 8)"
question="Question smoke test"
answer="Reponse smoke"
account_id=
account_created=0

cleanup_account() {
  local result summary player_count deleted

  [[ "$account_created" == 1 ]] || return 0
  [[ "$account" =~ ^smoke_1419[0-9a-f]+$ ]] || {
    printf 'Nettoyage refusé : marqueur de compte inattendu.\n' >&2
    return 1
  }
  [[ "$pseudo" =~ ^Smoke_[0-9a-f]+$ ]] || {
    printf 'Nettoyage refusé : marqueur de pseudo inattendu.\n' >&2
    return 1
  }
  [[ "$character" =~ ^Smoke-(x[a-p]){6}$ ]] || {
    printf 'Nettoyage refusé : marqueur de personnage inattendu.\n' >&2
    return 1
  }
  [[ "$email" == "${account}@invalid.local" ]] || {
    printf 'Nettoyage refusé : marqueur e-mail inattendu.\n' >&2
    return 1
  }
  if [[ -n "$account_id" && ! "$account_id" =~ ^[0-9]+$ ]]; then
    printf 'Nettoyage refusé : identifiant interne inattendu.\n' >&2
    return 1
  fi

  # Les quatre marqueurs doivent correspondre. Le comptage explicite rend
  # visible tout personnage résiduel et NOT EXISTS referme la course entre
  # ce comptage et le DELETE.
  result="$(
    {
      printf "SET @smoke_account = '%s';\n" "$account"
      printf "SET @smoke_pseudo = '%s';\n" "$pseudo"
      printf "SET @smoke_email = '%s';\n" "$email"
      printf "SET @reported_id = %s;\n" "${account_id:-0}"
      printf '%s\n' \
        "SET @smoke_id = IF(" \
        "  @reported_id > 0," \
        "  @reported_id," \
        "  (SELECT guid FROM world_accounts" \
        "   WHERE account = @smoke_account" \
        "     AND pseudo = @smoke_pseudo" \
        "     AND email = @smoke_email LIMIT 1)" \
        ");" \
        "SET @player_count = (" \
        "  SELECT COUNT(*) FROM world_players WHERE account = @smoke_id" \
        ");" \
        "DELETE FROM world_accounts" \
        "WHERE guid = @smoke_id" \
        "  AND account = @smoke_account" \
        "  AND pseudo = @smoke_pseudo" \
        "  AND email = @smoke_email" \
        "  AND NOT EXISTS (" \
        "    SELECT 1 FROM world_players WHERE account = @smoke_id" \
        "  );" \
        "SET @deleted = ROW_COUNT();" \
        "SELECT CONCAT(@player_count, '|', @deleted);"
    } | compose exec -T mariadb sh -ec \
      'export MYSQL_PWD="$(cat /run/secrets/starloco_db_password_secret)"; exec mariadb --protocol=socket -ustarloco starloco_login --batch --skip-column-names'
  )" || return 1

  summary="$(tail -n 1 <<<"$result" | tr -d '[:space:]')"
  player_count="${summary%%|*}"
  deleted="${summary#*|}"
  [[ "$player_count" == 0 ]] || {
    printf 'Nettoyage ciblé refusé : %s personnage(s) subsiste(nt) sur le compte.\n' \
      "$player_count" >&2
    return 1
  }
  [[ "$deleted" == 1 ]] || {
    printf 'Nettoyage ciblé refusé (compte absent ou marqueurs différents).\n' >&2
    return 1
  }
  printf 'Compte temporaire supprimé : %s (id %s).\n' "$account" "$account_id"
}

on_exit() {
  local status=$?
  trap - EXIT
  set +e
  if [[ "$account_created" == 1 ]]; then
    if [[ "$KEEP_ACCOUNT" == 1 ]]; then
      printf 'Compte de débogage conservé : compte=%s mot_de_passe=%s id=%s\n' \
        "$account" "$password" "${account_id:-inconnu}"
    elif ! cleanup_account; then
      printf 'Le compte temporaire n’a pas été supprimé automatiquement : %s (id %s).\n' \
        "$account" "${account_id:-inconnu}" >&2
      (( status == 0 )) && status=1
    fi
  fi
  exit "$status"
}
trap on_exit EXIT

printf 'Création du compte temporaire via le service du portail…\n'
set +e
creation_output="$(
  printf '%s\n' \
    "$account" "$pseudo" "$email" "$password" "$password" \
    "$question" "$answer" |
    compose exec -T web php /src/public/cli-create-account.php 2>&1
)"
creation_status=$?
set -e
if (( creation_status != 0 )); then
  printf '%s\n' "$creation_output" >&2
  die "La création du compte de smoke test a échoué."
fi
account_created=1
account_id="$(sed -n 's/.*identifiant interne \([0-9][0-9]*\).*/\1/p' <<<"$creation_output" | tail -n 1)"
[[ "$account_id" =~ ^[0-9]+$ ]] ||
  die "Le portail a créé le compte, mais son identifiant n'a pas pu être lu."

python_args=(
  "$ROOT/tests/protocol_1419.py"
  --host "$HOST"
  --login-port "$LOGIN_PORT"
  --game-port "$GAME_PORT"
  --server-id "$SERVER_ID"
  --timeout "$TIMEOUT"
  --account "$account"
  --character "$character"
  --expected-account-id "$account_id"
)
[[ "$SKIP_VERSION_REJECTION" == 1 ]] &&
  python_args+=(--skip-version-rejection)
[[ "$QUIET" == 1 ]] &&
  python_args+=(--quiet)

STARLOCO_SMOKE_ACCOUNT="$account" \
STARLOCO_SMOKE_PASSWORD="$password" \
STARLOCO_SMOKE_CHARACTER="$character" \
  python3 "${python_args[@]}"
