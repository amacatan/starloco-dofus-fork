#!/usr/bin/env python3
"""Smoke test socket pour Dofus Retro 1.41.9.

Ce programme ne crée ni ne supprime de compte. Le wrapper
``offline-smoke-test.sh`` lui fournit un compte temporaire créé par le portail.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import socket
import sys
import time
from dataclasses import dataclass
from typing import Callable


CLIENT_RELEASE = (1, 41, 9)
DEFAULT_CLIENT_VERSION = "1.41.9e"
PASSWORD_CHAIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
GAME_ERROR_PREFIXES = ("AAE", "ADE", "ALE", "ASE", "ATE")
SMOKE_CHARACTER_CLASS = 1
SMOKE_CHARACTER_SEX = 0
SMOKE_CHARACTER_COLORS = (-1, -1, -1)
FramePredicate = Callable[[str, list[str]], bool]


class ProtocolError(RuntimeError):
    """Le serveur a répondu, mais pas comme le protocole attendu."""


class DofusWire:
    """Connexion Dofus acceptant des trames NUL et newline.

    Les serveurs StarLoco encodent leurs réponses avec NUL. La prise en charge
    de newline facilite aussi le diagnostic d'autres builds, sans casser la
    policy XML qui contient elle-même plusieurs retours à la ligne.
    """

    def __init__(self, host: str, port: int, timeout: float, verbose: bool) -> None:
        self.host = host
        self.port = port
        self.timeout = timeout
        self.verbose = verbose
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.buffer = bytearray()

    def close(self) -> None:
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()

    def __enter__(self) -> "DofusWire":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def send_packet(self, packet: str, display: str | None = None) -> None:
        if "\n" in packet or "\0" in packet:
            raise ProtocolError("Une trame sortante contient un séparateur interdit.")
        if self.verbose:
            print(f"  -> {display if display is not None else packet}")
        self.sock.sendall(packet.encode("utf-8") + b"\n\0")

    def receive_until(
        self,
        predicate: FramePredicate,
        description: str,
        timeout: float | None = None,
    ) -> tuple[str, list[str]]:
        deadline = time.monotonic() + (self.timeout if timeout is None else timeout)
        frames: list[str] = []

        while True:
            frame = self._pop_frame()
            if frame is not None:
                frames.append(frame)
                if self.verbose:
                    shown = (
                        "<policy XML>"
                        if frame.startswith("<?xml") and "<cross-domain-policy>" in frame
                        else frame
                    )
                    print(f"  <- {shown}")
                if predicate(frame, frames):
                    return frame, frames
                continue

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                received = ", ".join(repr(frame) for frame in frames) or "aucune"
                raise ProtocolError(
                    f"Délai dépassé en attendant {description}; trames reçues: {received}"
                )

            self.sock.settimeout(remaining)
            try:
                chunk = self.sock.recv(65536)
            except socket.timeout as error:
                received = ", ".join(repr(frame) for frame in frames) or "aucune"
                raise ProtocolError(
                    f"Délai dépassé en attendant {description}; trames reçues: {received}"
                ) from error

            if not chunk:
                trailing = self._pop_trailing_frame()
                if trailing is not None:
                    frames.append(trailing)
                    if predicate(trailing, frames):
                        return trailing, frames
                received = ", ".join(repr(frame) for frame in frames) or "aucune"
                raise ProtocolError(
                    f"Connexion fermée en attendant {description}; trames reçues: {received}"
                )
            self.buffer.extend(chunk)

    def _pop_frame(self) -> str | None:
        while True:
            nul_position = self.buffer.find(b"\0")
            if nul_position >= 0:
                raw = bytes(self.buffer[:nul_position]).rstrip(b"\r\n")
                del self.buffer[: nul_position + 1]
                if not raw:
                    continue
                return raw.decode("utf-8", errors="replace")

            # Une policy XML StarLoco est une seule trame NUL contenant des
            # newlines. Tant qu'elle est incomplète, il ne faut pas la scinder.
            if self.buffer.lstrip().startswith(b"<"):
                return None

            newline_position = self.buffer.find(b"\n")
            if newline_position < 0:
                return None
            raw = bytes(self.buffer[:newline_position]).rstrip(b"\r")
            del self.buffer[: newline_position + 1]
            if not raw:
                continue
            return raw.decode("utf-8", errors="replace")

    def _pop_trailing_frame(self) -> str | None:
        raw = bytes(self.buffer).strip(b"\r\n\0")
        self.buffer.clear()
        return raw.decode("utf-8", errors="replace") if raw else None


@dataclass(frozen=True)
class GameTicket:
    host: str
    port: int
    account_id: int


def parse_release(version: str) -> tuple[int, int, int]:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:e)?", version)
    if match is None:
        raise ProtocolError(f"Version client invalide: {version!r}")
    return tuple(int(value) for value in match.groups())  # type: ignore[return-value]


def previous_revision(version: str) -> str:
    major, minor, revision = parse_release(version)
    suffix = "e" if version.endswith("e") else ""
    return f"{major}.{minor}.{max(0, revision - 1)}{suffix}"


def encrypt_password(password: str, key: str) -> str:
    if not key:
        raise ProtocolError("La clef HC est vide.")
    if len(password) > len(key):
        raise ProtocolError(
            f"Le mot de passe ({len(password)} caractères) dépasse la clef HC "
            f"({len(key)} caractères)."
        )

    encrypted: list[str] = []
    for index, character in enumerate(password):
        value = ord(character)
        key_value = ord(key[index])
        encrypted.append(PASSWORD_CHAIN[((value >> 4) + key_value) % 64])
        encrypted.append(PASSWORD_CHAIN[((value & 15) + key_value) % 64])
    return "".join(encrypted)


def verify_policy_and_get_key(wire: DofusWire) -> str:
    hc_frame, frames = wire.receive_until(
        lambda frame, _frames: frame.startswith("HC"),
        "la clef de connexion HC",
    )
    if not any("<cross-domain-policy>" in frame for frame in frames):
        raise ProtocolError("La policy cross-domain n'a pas été reçue avant HC.")
    key = hc_frame[2:]
    if not re.fullmatch(r"[a-z]{32}", key):
        raise ProtocolError(f"Format de clef HC inattendu: {key!r}")
    return key


def reject_errors(frame: str, stage: str) -> None:
    if frame.startswith(("AlE", "AXE", "ATE")):
        raise ProtocolError(f"Erreur serveur pendant {stage}: {frame}")


def reject_game_errors(frames: list[str], stage: str) -> None:
    for frame in frames:
        if frame.startswith(GAME_ERROR_PREFIXES):
            raise ProtocolError(f"Erreur serveur pendant {stage}: {frame}")


def receive_required_prefixes(
    wire: DofusWire,
    prefixes: tuple[str, ...],
    stage: str,
    timeout: float | None = None,
) -> list[str]:
    """Attend toutes les familles de trames, ou s'arrête sur une erreur jeu."""

    _, frames = wire.receive_until(
        lambda frame, received: (
            frame.startswith(GAME_ERROR_PREFIXES)
            or all(any(item.startswith(prefix) for item in received) for prefix in prefixes)
        ),
        f"{stage} ({', '.join(prefixes)})",
        timeout=timeout,
    )
    reject_game_errors(frames, stage)
    missing = [
        prefix
        for prefix in prefixes
        if not any(frame.startswith(prefix) for frame in frames)
    ]
    if missing:
        raise ProtocolError(
            f"Trames absentes pendant {stage}: {', '.join(missing)}."
        )
    return frames


def parse_character_list(frame: str) -> tuple[int, dict[int, str]]:
    """Décode le nombre et les identités d'une trame ALK."""

    header = re.match(r"^ALK\d+\|(\d+)", frame)
    if header is None:
        raise ProtocolError(f"Liste de personnages ALK mal formée: {frame!r}")

    announced_count = int(header.group(1))
    characters: dict[int, str] = {}
    for match in re.finditer(r"\|(\d+);([^;|]+);", frame[header.end() :]):
        character_id = int(match.group(1))
        character_name = match.group(2)
        if character_id <= 0 or character_id in characters:
            raise ProtocolError(f"Identifiant invalide ou dupliqué dans ALK: {frame!r}")
        characters[character_id] = character_name

    if len(characters) != announced_count:
        raise ProtocolError(
            f"ALK annonce {announced_count} personnage(s), "
            f"mais {len(characters)} entrée(s) ont été décodées: {frame!r}"
        )
    return announced_count, characters


def derive_character_name(account: str) -> str:
    """Produit un nom unique, uniquement alphabétique et sans lettre triplée."""

    translated = hashlib.sha256(account.encode("utf-8")).hexdigest()[:6].translate(
        str.maketrans("0123456789abcdef", "ghijklmnopabcdef")
    )
    return "Smoke-" + "".join(f"x{character}" for character in translated)


def check_old_version_rejected(args: argparse.Namespace) -> None:
    rejected_version = previous_revision(args.version)
    print(f"[version] Contrôle du verrou ({rejected_version} doit être refusée)")
    with DofusWire(args.host, args.login_port, args.timeout, args.verbose) as wire:
        verify_policy_and_get_key(wire)
        wire.send_packet(f"{rejected_version}|{args.language}")
        frame, _ = wire.receive_until(
            lambda item, _frames: item.startswith("AlEv"),
            "le refus AlEv de l'ancienne version",
        )
        expected = ".".join(str(value) for value in CLIENT_RELEASE)
        if frame != f"AlEv{expected}":
            raise ProtocolError(
                f"Refus de version inattendu: {frame!r}, attendu: {'AlEv' + expected!r}"
            )


def login_and_select_server(
    args: argparse.Namespace,
    password: str,
    phase: str,
) -> GameTicket:
    print(f"[login {phase}] Authentification avec le client {args.version}")
    with DofusWire(args.host, args.login_port, args.timeout, args.verbose) as wire:
        key = verify_policy_and_get_key(wire)
        encrypted_password = encrypt_password(password, key)

        wire.send_packet(f"{args.version}|{args.language}")
        wire.send_packet(args.account)
        wire.send_packet("#1" + encrypted_password, display="#1<mot-de-passe-chiffré>")
        wire.send_packet("Af")

        _, information_frames = wire.receive_until(
            lambda frame, frames: (
                frame.startswith(("AlE", "AXE", "ATE"))
                or (
                    any(item.startswith("AlK") for item in frames)
                    and any(item.startswith("AQ") for item in frames)
                    and any(item.startswith("AH") for item in frames)
                )
            ),
            "les informations du compte (AH, AlK et AQ)",
        )
        for frame in information_frames:
            reject_errors(frame, "l'authentification login")

        host_list = next(frame for frame in information_frames if frame.startswith("AH"))
        expected_server = f"{args.server_id};1;"
        if expected_server not in host_list:
            raise ProtocolError(
                f"Le serveur {args.server_id} n'est pas annoncé disponible dans {host_list!r}."
            )

        wire.send_packet("Ax")
        server_list, _ = wire.receive_until(
            lambda frame, _frames: frame.startswith(("AxK", "AXE", "AlE")),
            "la liste AxK des serveurs",
        )
        reject_errors(server_list, "la liste des serveurs")
        if re.search(rf"(?:^|\|){args.server_id},\d+(?:\||$)", server_list[3:]) is None:
            raise ProtocolError(
                f"Le serveur {args.server_id} est absent de la liste {server_list!r}."
            )

        print(f"[monde {phase}] Sélection du monde {args.server_id}")
        wire.send_packet(f"AX{args.server_id}")
        ticket_frame, _ = wire.receive_until(
            lambda frame, _frames: frame.startswith(("AYK", "AXE", "AlE")),
            "le ticket AYK du serveur de jeu",
        )
        reject_errors(ticket_frame, "la sélection du serveur")
        ticket = parse_game_ticket(ticket_frame)

        if args.expected_account_id is not None and ticket.account_id != args.expected_account_id:
            raise ProtocolError(
                f"AYK contient le compte {ticket.account_id}, "
                f"mais {args.expected_account_id} était attendu."
            )
        if ticket.port != args.game_port:
            raise ProtocolError(
                f"AYK annonce le port {ticket.port}, mais {args.game_port} était attendu."
            )
        if ticket.host not in {args.host, "127.0.0.1", "localhost"}:
            raise ProtocolError(
                f"AYK annonce l'hôte inattendu {ticket.host!r}; "
                f"{args.host!r} ou une boucle locale était attendue."
            )
        return ticket


def reconnect_and_select_server(
    args: argparse.Namespace,
    password: str,
) -> GameTicket:
    """Absorbe uniquement l'état connecté transitoire après sessionClosed."""

    deadline = time.monotonic() + args.timeout
    last_rejection: ProtocolError | None = None
    while time.monotonic() < deadline:
        try:
            return login_and_select_server(args, password, "nettoyage")
        except ProtocolError as error:
            if not any(code in str(error) for code in ("AlEa", "AlEc", "AlEd")):
                raise
            last_rejection = error
            time.sleep(0.1)
    raise ProtocolError(
        "Le compte est resté annoncé connecté après la fermeture de la "
        f"première session (dernière réponse: {last_rejection})."
    )


def parse_game_ticket(frame: str) -> GameTicket:
    if not frame.startswith("AYK"):
        raise ProtocolError(f"Ticket de jeu invalide: {frame!r}")
    try:
        endpoint, raw_account_id = frame[3:].rsplit(";", 1)
        host, raw_port = endpoint.rsplit(":", 1)
        ticket = GameTicket(host=host, port=int(raw_port), account_id=int(raw_account_id))
    except (ValueError, TypeError) as error:
        raise ProtocolError(f"Ticket AYK mal formé: {frame!r}") from error
    if not ticket.host or not (1 <= ticket.port <= 65535) or ticket.account_id <= 0:
        raise ProtocolError(f"Ticket AYK hors limites: {frame!r}")
    return ticket


def open_game_connection(args: argparse.Namespace, ticket: GameTicket) -> DofusWire:
    # AYK est envoyé juste après WA sur deux sockets serveur distinctes. Une
    # courte reprise sur ATE absorbe donc une éventuelle course réseau sans
    # masquer une autre erreur de protocole.
    deadline = time.monotonic() + args.timeout
    last_rejection: str | None = None
    while time.monotonic() < deadline:
        wire = DofusWire(ticket.host, ticket.port, args.timeout, args.verbose)
        try:
            wire.send_packet(f"AT{ticket.account_id}")
            attribute, _ = wire.receive_until(
                lambda frame, _frames: frame.startswith(("ATK", "ATE")),
                "la validation ATK du ticket",
                timeout=max(0.2, deadline - time.monotonic()),
            )
            if attribute == "ATE":
                last_rejection = attribute
                wire.close()
                time.sleep(0.1)
                continue
            if attribute != "ATK0":
                raise ProtocolError(
                    f"Le serveur a activé un mode de chiffrement non attendu: {attribute!r}."
                )
            return wire
        except Exception:
            wire.close()
            raise

    raise ProtocolError(
        f"Le ticket n'a pas atteint le serveur de jeu avant expiration "
        f"(dernière réponse: {last_rejection or 'aucune'})."
    )


def get_character_list(
    wire: DofusWire,
    timeout: float,
    stage: str,
) -> tuple[int, dict[int, str]]:
    wire.send_packet("AL")
    frames = receive_required_prefixes(wire, ("ALK",), stage, timeout=timeout)
    character_list = next(frame for frame in frames if frame.startswith("ALK"))
    return parse_character_list(character_list)


def create_select_and_load_map(
    args: argparse.Namespace,
    ticket: GameTicket,
) -> int:
    print(
        f"[jeu création] Ticket {ticket.host}:{ticket.port}, "
        f"création de {args.character}"
    )
    wire = open_game_connection(args, ticket)
    try:
        count, characters = get_character_list(
            wire,
            args.timeout,
            "la liste initiale des personnages",
        )
        if count != 0 or characters:
            raise ProtocolError(
                f"Le compte temporaire n'est pas vide avant création: {characters!r}"
            )

        character_packet = "|".join(
            (
                args.character,
                str(SMOKE_CHARACTER_CLASS),
                str(SMOKE_CHARACTER_SEX),
                *(str(color) for color in SMOKE_CHARACTER_COLORS),
            )
        )
        wire.send_packet("AA" + character_packet)
        creation_frames = receive_required_prefixes(
            wire,
            ("AAK", "ALK"),
            "la création du personnage",
            timeout=args.timeout,
        )
        created_list = next(
            frame for frame in creation_frames if frame.startswith("ALK")
        )
        count, characters = parse_character_list(created_list)
        matching_ids = [
            character_id
            for character_id, name in characters.items()
            if name == args.character
        ]
        if count != 1 or len(matching_ids) != 1:
            raise ProtocolError(
                f"Le personnage créé n'est pas l'unique entrée de ALK: {created_list!r}"
            )
        character_id = matching_ids[0]

        print(f"[jeu sélection] Sélection du personnage {character_id}")
        wire.send_packet(f"AS{character_id}")
        bootstrap_frames = receive_required_prefixes(
            wire,
            ("ASK", "Ow", "SL"),
            "l'initialisation du personnage",
            timeout=args.timeout,
        )
        expected_ask = f"ASK|{character_id}|{args.character}|"
        ask_frame = next(
            (
                frame
                for frame in bootstrap_frames
                if frame.startswith(expected_ask)
            ),
            None,
        )
        if ask_frame is None:
            raise ProtocolError(
                f"ASK ne décrit pas le personnage créé {character_id}/{args.character}."
            )
        ask_fields = ask_frame.split("|")
        if (
            len(ask_fields) < 10
            or ask_fields[3] != "1"
            or ask_fields[4] != str(SMOKE_CHARACTER_CLASS)
            or ask_fields[5] != str(SMOKE_CHARACTER_SEX)
            or tuple(ask_fields[7:10]) != tuple(
                str(color) for color in SMOKE_CHARACTER_COLORS
            )
        ):
            raise ProtocolError(
                "ASK ne confirme pas le niveau 1, la classe, le sexe et "
                f"les couleurs demandés: {ask_frame!r}"
            )
        pod_frame = next(frame for frame in bootstrap_frames if frame.startswith("Ow"))
        if re.fullmatch(r"Ow\d+\|\d+", pod_frame) is None:
            raise ProtocolError(f"Trame de pods Ow mal formée: {pod_frame!r}")

        print("[jeu carte] Demande GC1 et chargement des données de carte")
        wire.send_packet("GC1")
        map_frames = receive_required_prefixes(
            wire,
            ("GCK", "GDM"),
            "le chargement de la carte",
            timeout=args.timeout,
        )
        expected_gck = f"GCK|1|{args.character}"
        if expected_gck not in map_frames:
            raise ProtocolError(
                f"Confirmation GCK inattendue; {expected_gck!r} était attendue."
            )
        map_data = next(frame for frame in map_frames if frame.startswith("GDM"))
        if re.fullmatch(r"GDM\|\d+\|[^|]+\|[^|]*", map_data) is None:
            raise ProtocolError(f"Trame de carte GDM mal formée: {map_data!r}")
        return character_id
    finally:
        wire.close()


def delete_character_after_reconnect(
    args: argparse.Namespace,
    ticket: GameTicket,
    character_id: int,
) -> None:
    print(
        f"[jeu nettoyage] Nouveau ticket {ticket.host}:{ticket.port}, "
        f"suppression du personnage {character_id}"
    )
    wire = open_game_connection(args, ticket)
    try:
        count, characters = get_character_list(
            wire,
            args.timeout,
            "la liste avant suppression",
        )
        if count != 1 or characters.get(character_id) != args.character:
            raise ProtocolError(
                f"ALK ne contient pas uniquement le personnage à supprimer: {characters!r}"
            )

        wire.send_packet(f"AD{character_id}")
        deletion_frames = receive_required_prefixes(
            wire,
            ("ALK",),
            "la suppression du personnage",
            timeout=args.timeout,
        )
        deleted_list = next(
            frame for frame in deletion_frames if frame.startswith("ALK")
        )
        remaining_count, remaining_characters = parse_character_list(deleted_list)
        if remaining_count != 0 or remaining_characters:
            raise ProtocolError(
                f"Le personnage subsiste après AD{character_id}: {deleted_list!r}"
            )
    finally:
        wire.close()


def internal_self_check() -> None:
    key = "abcdefghijklmnopqrstuvwxyzabcdef"
    password = "Retro1419"
    encrypted = encrypt_password(password, key)
    if len(encrypted) != len(password) * 2:
        raise ProtocolError("Auto-test du chiffrement Dofus échoué.")

    wire = object.__new__(DofusWire)
    wire.buffer = bytearray(
        b'<?xml version="1.0"?>\n<cross-domain-policy></cross-domain-policy>\0'
        b"HCabcdefghijklmnopqrstuvwxyzabcdef\0AH601;1;110;1\n"
    )
    first = wire._pop_frame()
    second = wire._pop_frame()
    third = wire._pop_frame()
    if (
        first is None
        or "<cross-domain-policy>" not in first
        or second != "HCabcdefghijklmnopqrstuvwxyzabcdef"
        or third != "AH601;1;110;1"
    ):
        raise ProtocolError("Auto-test du découpage des trames échoué.")

    expected = GameTicket("127.0.0.1", 5555, 42)
    if parse_game_ticket("AYK127.0.0.1:5555;42") != expected:
        raise ProtocolError("Auto-test du ticket AYK échoué.")

    count, characters = parse_character_list(
        "ALK86400000|1|123;Smoke-xaxbxcxdxexf;1;10;-1;-1;-1;;0;601;0"
    )
    if count != 1 or characters != {123: "Smoke-xaxbxcxdxexf"}:
        raise ProtocolError("Auto-test du décodage ALK échoué.")
    derived_name = derive_character_name("smoke_1419abcdef")
    if re.fullmatch(r"Smoke-(?:x[a-p]){6}", derived_name) is None:
        raise ProtocolError("Auto-test du nom de personnage échoué.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Teste le parcours Dofus Retro 1.41.9: login 450, monde 601, "
            "création/sélection d'un personnage, carte et suppression après "
            "reconnexion sur le port jeu 5555."
        )
    )
    parser.add_argument("--host", default="127.0.0.1", help="hôte de la pile")
    parser.add_argument("--login-port", type=int, default=450, help="port login")
    parser.add_argument("--game-port", type=int, default=5555, help="port jeu")
    parser.add_argument("--server-id", type=int, default=601, help="identifiant du monde")
    parser.add_argument(
        "--version",
        default=DEFAULT_CLIENT_VERSION,
        help="version émise par le client (défaut: 1.41.9e)",
    )
    parser.add_argument("--language", default="fr", help="langue du client")
    parser.add_argument("--account", help="compte de test; ou STARLOCO_SMOKE_ACCOUNT")
    parser.add_argument(
        "--character",
        help=(
            "nom du personnage temporaire; ou STARLOCO_SMOKE_CHARACTER "
            "(dérivé du compte par défaut)"
        ),
    )
    parser.add_argument(
        "--expected-account-id",
        type=int,
        help="identifiant interne attendu dans AYK",
    )
    parser.add_argument("--timeout", type=float, default=8.0, help="délai par étape")
    parser.add_argument(
        "--skip-version-rejection",
        action="store_true",
        help="ne vérifie pas qu'une révision plus ancienne est refusée",
    )
    parser.add_argument("--quiet", action="store_true", help="masque les trames")
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="teste uniquement le chiffrement et le découpage locaux",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        internal_self_check()
        if args.self_test:
            print("OK - auto-tests locaux du protocole 1.41.9")
            return 0

        args.account = args.account or os.environ.get("STARLOCO_SMOKE_ACCOUNT")
        args.character = (
            args.character
            or os.environ.get("STARLOCO_SMOKE_CHARACTER")
            or (derive_character_name(args.account) if args.account else None)
        )
        password = os.environ.get("STARLOCO_SMOKE_PASSWORD")
        if not args.account:
            raise ProtocolError(
                "--account ou la variable STARLOCO_SMOKE_ACCOUNT est obligatoire."
            )
        if not password:
            raise ProtocolError("La variable STARLOCO_SMOKE_PASSWORD est obligatoire.")
        if parse_release(args.version) != CLIENT_RELEASE:
            raise ProtocolError(
                f"Ce test est dédié à 1.41.9, pas à {args.version!r}."
            )
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{2,29}", args.account):
            raise ProtocolError(
                "Le nom de compte ne respecte pas le contrat du portail/login."
            )
        if args.character is None or re.fullmatch(
            r"[A-Za-z-]{3,20}", args.character
        ) is None:
            raise ProtocolError("Le nom du personnage temporaire est invalide.")
        character_letters = args.character.lower().replace("-", "")
        if args.character.count("-") > 6 or re.search(
            r"([a-z])\1\1", character_letters
        ):
            raise ProtocolError(
                "Le nom du personnage enfreint les contraintes du serveur."
            )
        if args.timeout <= 0:
            raise ProtocolError("--timeout doit être strictement positif.")
        for port in (args.login_port, args.game_port):
            if not 1 <= port <= 65535:
                raise ProtocolError(f"Port invalide: {port}")

        args.verbose = not args.quiet
        if not args.skip_version_rejection:
            check_old_version_rejected(args)
        ticket = login_and_select_server(args, password, "initial")
        character_id = create_select_and_load_map(args, ticket)

        # Laisse le serveur traiter sessionClosed avant la nouvelle
        # authentification, sans transformer ce délai en mécanisme de masquage.
        time.sleep(min(0.25, args.timeout / 10))
        cleanup_ticket = reconnect_and_select_server(args, password)
        delete_character_after_reconnect(args, cleanup_ticket, character_id)
        print(
            "OK - Dofus Retro 1.41.9e: authentification, monde 601, "
            "création/sélection, inventaire, sorts, carte, reconnexion et "
            "suppression du personnage validés."
        )
        return 0
    except (OSError, ProtocolError) as error:
        print(f"ÉCHEC - {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
