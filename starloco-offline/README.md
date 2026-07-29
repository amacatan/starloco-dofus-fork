# StarLoco Offline — Dofus Retro 1.41.9

Ce dossier fournit une pile StarLoco autonome pour un serveur Ubuntu 24.04 et une connexion directe depuis le client Dofus Retro 1.41.9 fourni. Les images Docker, leur somme SHA-256, les sources modifiées et la base de jeu sont incluses : aucune connexion Internet n’est requise pour démarrer la pile préparée.

Le seul fichier modifié dans le client est `Retro/resources/app/retroclient/config.xml`. Le détail est dans [CLIENT-DOFUS-RETRO-1.41.9.md](CLIENT-DOFUS-RETRO-1.41.9.md).

## Prérequis

- Ubuntu 24.04 avec Docker Engine démarré ;
- le plugin Docker Compose ;
- Bash ;
- suffisamment d’espace pour l’archive d’images, ses images chargées et le volume MariaDB.

Java, Gradle, PHP, MariaDB et Redis n’ont pas à être installés sur l’hôte : ils s’exécutent dans Docker.

## Démarrage rapide

Depuis `starloco-offline` :

```bash
./offline-start.sh
```

Au premier démarrage, le script :

1. crée la configuration et les secrets locaux s’ils sont absents ;
2. vérifie la somme de contrôle de `docker-images/starloco-images.tar`, puis charge les images manquantes ;
3. initialise MariaDB ou met à niveau le volume existant ;
4. applique et vérifie les migrations Login et Game avant d’ouvrir les services ;
5. démarre la pile et attend que chaque service soit sain.

L’import initial de la base de jeu peut prendre plusieurs minutes. Un second lancement de `offline-start.sh` est normal et sans rejeu destructif des migrations.

Créez ensuite un compte :

```text
http://127.0.0.1/register.php
```

ou dans le terminal :

```bash
./offline-create-account.sh
```

Le portail propose également :

```text
http://127.0.0.1/          fil communautaire
http://127.0.0.1/players   annuaire et fiches publiques
http://127.0.0.1/admin/    console d’administration locale
```

Les accès administrateur sont générés au premier démarrage. Pour les afficher :

```bash
./offline-admin-password.sh
```

Puis lancez le client configuré dans un environnement Windows compatible et choisissez `StarLoco local`. Le lancement sous Wine n’a pas été validé dans cet audit.

## Contrôles recommandés

Une fois la pile démarrée :

```bash
./offline-doctor.sh --database
./offline-smoke-test.sh
```

Le premier contrôle vérifie notamment les schémas, migrations, checksums SQL, services et healthchecks. Le second crée un compte et un personnage temporaires, parcourt le flux réseau 1.41.9, puis les supprime de façon ciblée.

Pour contrôler les fichiers du paquet sans migration :

```bash
./offline-verify.sh
```

## Services

La pile complète contient exactement six services :

| Service | Rôle | Port publié par défaut |
|---|---|---|
| `mariadb` | comptes et données de jeu | aucun |
| `redis` | cache du serveur de jeu | aucun |
| `login` | authentification directe Retro | `127.0.0.1:450` |
| `game` | monde StarLoco | `127.0.0.1:5555` |
| `web` | portail, annuaire et console d’administration | `127.0.0.1:80` |
| `social-worker` | collecte locale du fil | aucun |

Le port d’échange `666` reste interne au réseau Docker. L’ancienne API HTTP Zaap n’est ni construite ni démarrée : elle n’est pas compatible avec le protocole launcher du client et n’est pas nécessaire à la connexion directe. Les Zaaps de téléportation dans le jeu ne sont pas concernés.

Les anciens conteneurs devenus orphelins sont retirés au prochain démarrage ou arrêt, sans suppression des volumes.

## Mise à niveau d’une installation existante

Sauvegardez le volume MariaDB avant toute mise à niveau importante. Utilisez ensuite la même commande que pour un démarrage normal :

```bash
./offline-start.sh
```

`offline-start.sh` arrête d’abord les services applicatifs, démarre MariaDB, puis lance `offline-migrate.sh`. Les correctifs Game 05 à 09 sont enregistrés avec leur checksum et ne sont appliqués qu’une fois. Si le correctif 08 doit recréer `quest_progress`, son contenu est copié dans une table de sauvegarde temporaire, restauré et vérifié avant suppression de cette sauvegarde.

Sur une ancienne base, les comptes publics `test`, `test2` et `test3` sont seulement verrouillés s’ils possèdent encore exactement le mot de passe de démonstration. Leurs personnages ne sont pas supprimés. Sur un volume neuf, les données de démonstration inchangées sont retirées pendant l’initialisation.

Il est déconseillé d’appeler directement la migration complète pendant que la pile tourne ; le script la refuse si Login, Game ou le portail sont actifs.

## Accès depuis le réseau local

Par défaut, tous les ports publiés écoutent uniquement sur `127.0.0.1`.

Pour un client installé sur une autre machine :

1. modifiez `runtime/stack/.env` ;
2. définissez `BIND_ADDRESS` et `GAME_SERVER_IP` avec l’adresse LAN du serveur Ubuntu ;
3. mettez la même adresse dans le `connserver` du `config.xml` client ;
4. redémarrez avec `./offline-start.sh`.

N’exposez pas MariaDB, Redis ou le port `666`. Le portail utilise le format historique de mot de passe exigé par StarLoco ; il est prévu pour un usage local ou privé, pas pour une exposition Internet publique.

La console `/admin/` est en plus filtrée par Nginx aux adresses locales, réseaux privés et adresses Tailscale. Elle fonctionne en HTTP sur le réseau privé : ne l’exposez jamais directement à Internet. Pour une administration à distance, préférez un tunnel SSH vers `127.0.0.1:80`.

## Console d’administration

La console permet de rechercher un personnage puis de :

- définir ou ajuster ses kamas ;
- modifier ses caractéristiques de base, ses points et son énergie ;
- rechercher un template et ajouter un objet avec un jet de base ou parfait ;
- retirer totalement ou partiellement une pile d’objets ;
- bannir ou débannir son compte ;
- consulter l’historique détaillé des mutations.

Un personnage doit être entièrement hors ligne avant toute modification. Un combattant déconnecté mais encore conservé par le serveur reste verrouillé. Chaque mutation prend un verrou par personnage, s’exécute dans une transaction InnoDB avec son entrée d’audit, puis force le serveur Game à recharger les données à la connexion suivante. Les familiers, certificats, objets vivants et autres formats spéciaux restent volontairement protégés car ils possèdent des tables annexes.

Le mot de passe administrateur est un secret local aléatoire. Le conteneur web ne reçoit qu’une empreinte Argon2id dérivée au démarrage. Pour renouveler le secret et fermer toutes les sessions existantes :

```bash
./offline-admin-password.sh --reset
```

La session admin est distincte de l’inscription, protégée par CSRF, limitée dans le temps et rate-limitée. Le journal ne stocke ni mot de passe, ni e-mail, ni adresse IP en clair.

## Comptes

Les deux méthodes de création appliquent le même contrat :

| Champ | Règle principale |
|---|---|
| Compte | 3–30 caractères ASCII, lettres, chiffres, `.`, `_` ou `-` ; normalisé en minuscules |
| Pseudo | 3–30 caractères simples |
| E-mail | adresse valide et unique, 100 caractères maximum |
| Mot de passe | 8–32 caractères latins imprimables, avec au moins une lettre et un chiffre |
| Question secrète | 5–100 caractères |
| Réponse secrète | 2–100 caractères |

La limite de 32 caractères vient du chiffrement historique du client Retro. Les doublons de compte, e-mail et pseudo sont contrôlés en base de façon transactionnelle.

## Commandes utiles

```bash
./offline-status.sh
./offline-logs.sh
./offline-logs.sh game
./offline-stop.sh
./offline-load-images.sh
./offline-admin-password.sh
```

Pour démarrer seulement MariaDB, le portail et le collecteur :

```bash
./offline-start.sh --portal-only
```

Les données persistantes résident dans les volumes `starloco_mariadb_data` et `starloco_redis_data`.

Les secrets actifs sont dans `runtime/stack/secrets/` et le fichier local `runtime/stack/.env`. Ils sont créés avec des permissions restrictives et exclus des sommes de contrôle générales. Une copie de ce dossier doit néanmoins être considérée comme contenant ses secrets et ne doit pas être publiée telle quelle.

## Développement et reconstruction

Les sources éditables du jeu sont dans `serveur-jeu/`; celles du serveur Login sont dans `sources/StarLoco-Login/`.

```bash
./game-dev.sh test
./game-dev.sh deploy
./offline-rebuild.sh login
./offline-rebuild.sh all
```

Chaque reconstruction Java exécute `clean check jar` hors ligne avant de construire l’image. Le déploiement Game conserve une image de retour arrière et restaure celle-ci si le nouveau conteneur ne devient pas sain.

`prepare-online.sh` sert uniquement à refaire les sources, caches et l’archive d’images sur une machine connectée. Le démarrage courant n’en a pas besoin.

## État de validation

La pile fraîche a été validée avec ses six services sains. Les builds Game et Login, les migrations relançables, les tests PHP et un parcours socket 1.41.9e jusqu’au chargement de la carte 10300 et à la reconnexion ont réussi. La console admin et les pages publiques possèdent en plus des contrôles HTTP et SQL automatisés.

Cette validation n’est pas un test graphique complet du client Windows. Wine, un combat entier et toutes les fonctions de l’interface étendue ajoutées en 1.41 n’ont pas été validés. Consultez [AUDIT.md](AUDIT.md) pour le périmètre exact et les limites.
