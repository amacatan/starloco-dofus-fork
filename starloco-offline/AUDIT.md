# Audit final — compatibilité Dofus Retro 1.41.9

Date : 30 juillet 2026

## Conclusion

Le paquet a été adapté pour une pile StarLoco Docker ciblant Ubuntu 24.04 et une connexion directe depuis Dofus Retro 1.41.9. Le parcours réseau automatisé 1.41.9e passe de l’authentification au chargement d’une carte, avec création, sélection, reconnexion et suppression d’un personnage temporaire.

Cette conclusion porte sur le serveur, les migrations et ce parcours protocolaire. Elle ne signifie pas que chaque activité du jeu a été testée manuellement dans l’interface graphique.

## Architecture livrée

La pile par défaut contient six services :

- `mariadb` ;
- `redis` ;
- `login` ;
- `game` ;
- `web` ;
- `social-worker`.

Seuls Login (`450`), Game (`5555`) et le portail (`80`) sont publiés, sur `127.0.0.1` par défaut. MariaDB, Redis et l’échange Login/Game sur `666` restent internes.

L’API HTTP appelée « Zaap » dans l’ancien déploiement a été retirée. Elle n’implémentait pas le protocole launcher attendu sur `26117` et n’était pas utilisée par l’authentification directe retenue. Ce retrait ne modifie ni les cartes ni les Zaaps de téléportation du jeu.

Le paquet comprend l’archive d’images `docker-images/starloco-images.tar` et sa somme SHA-256 dédiée. Le chargeur refuse l’archive si cette somme est absente ou incohérente.

## Adaptations 1.41.9

### Client

- Un seul fichier client a été modifié : `Retro/resources/app/retroclient/config.xml`.
- Le profil local annonce directement `127.0.0.1:450`.
- Le paramètre launcher `zaapconnectport` a été retiré.
- Les databanks locales du client ont été conservées.

### Login et version

- La version admise est la release `1.41.9`, avec ou sans suffixe `e`.
- Les versions antérieures, supérieures ou mal formées sont refusées au lieu d’être comparées comme de simples chaînes.
- La lecture des paquets Login et du mot de passe chiffré a été durcie contre les valeurs nulles, tronquées, impaires ou hors alphabet.
- Les noms de compte contenant `_` sont recherchés avec une égalité SQL et non
  avec `LIKE`; le chemin JWS applique le même contrat de nom avant accès à la
  base.
- La liste des mondes ne publie que les serveurs connectés et ouverts ; le monde local `601` est correctement proposé à un nouveau compte.
- Une nouvelle session d’échange authentifiée remplace l’ancienne et une session obsolète ne peut plus piloter l’état du serveur.
- Les paquets sensibles Login, les tickets Game et la clé serveur sont masqués
  dans les journaux et sorties d’administration.

### Game et échange

- Les paquets vides ne provoquent plus d’accès hors limites.
- Le ping rapide utilisé par Retro 1.41 (`qping`) reçoit désormais sa réponse
  protocolaire `q`.
- L’horloge `BT` utilise directement l’époque Unix courante, sans décalage
  horaire fixe qui rendait l’heure fausse selon le fuseau et l’heure d’été.
- Les paquets enveloppés avec le séparateur `ù` conservent leur charge utile
  complète, y compris quand un message de discussion contient lui-même ce
  caractère. Les trames enveloppées incomplètes sont ignorées.
- L’extraction et le filtrage des adresses distantes acceptent IPv4 et IPv6
  côté Login comme côté Game.
- Les notifications de connexion d’un ami et l’affichage détaillé de `/whois`
  comparent désormais des identifiants de compte, et non des identifiants de
  personnage.
- Une commande d’administration reçue avant l’initialisation complète du
  personnage ferme proprement la session au lieu de provoquer une erreur
  nulle.
- Les tickets en attente sont stockés de façon concurrente.
- Les erreurs de bind sont retentées de façon bornée sans récursion.
- Les modifications de points passent par une mise à jour SQL transactionnelle portable.
- Les chemins déjà présents pour les réponses de raccourcis de sorts 1.41 (`SR`/`SM`), les champs de statistiques d’objet vides et le préfixe `version|langue` ont été conservés et compilés avec le reste.
- Une ligne `extra_monster` dont la zone ne désigne aucune carte valide est ignorée au chargement au lieu de produire une erreur de démarrage.
- Un drop dont le monstre ou le template d’objet est absent est ignoré avant
  d’atteindre les branches normale ou globale. Les deux références orphelines
  connues (`11009` et `12878`) ne peuvent donc créer ni objet fantôme ni erreur
  de fin de combat.

Ces changements visent le socle protocolaire classique. Ils n’ajoutent pas les nouvelles fonctions d’interface étendue 1.41 décrites dans les limites ci-dessous.

## Données et migrations

La base complète `04-game.sql` de StarLoco Game `v1.0.6` et les correctifs Game 05 à 09 sont livrés dans `runtime/stack/db-init/`.

Pour une base existante, `offline-start.sh` :

1. arrête les services applicatifs ;
2. démarre et attend MariaDB ;
3. applique les migrations Login et Game ;
4. vérifie l’état final et le checksum de chaque correctif Game ;
5. redémarre les applications uniquement après succès.

Les correctifs Game sont inscrits dans `starloco_schema_migrations`. Un fichier déjà enregistré avec un autre checksum provoque un arrêt explicite.

Le correctif 08 recrée historiquement `quest_progress`. Avant son application, le paquet copie les six colonnes attendues dans `starloco_quest_progress_before_08`, restaure les lignes après le correctif, vérifie le schéma final, puis retire la table temporaire. Une restauration différée est également tentée au lancement suivant si une exécution a été interrompue.

Le schéma `world_accounts` a été aligné avec Login et le portail :

- moteur InnoDB ;
- valeurs historiques nulles réparées avant ajout des contraintes ;
- dates normalisées en `YYYY-MM-DD` ;
- longueurs cohérentes avec les champs client ;
- index e-mail et pseudo relançables ;
- assertions SQL bloquantes sur un schéma incompatible.

Sur un volume existant, les comptes `test`, `test2` et `test3` sont verrouillés uniquement si leur hachage correspond encore au mot de passe public du dump. Leurs personnages sont conservés. Le nettoyage des comptes de démonstration inchangés et de l’orphelin connu n’est effectué automatiquement que lors de l’initialisation d’un volume neuf.

## Portail et comptes

Le portail et la commande CLI utilisent la même validation :

- insertion limitée aux colonnes réellement présentes ;
- requêtes PDO préparées ;
- transaction InnoDB et verrou applicatif pour les créations concurrentes ;
- conflits compte, e-mail et pseudo distingués ;
- protection CSRF, champ leurre et limites de débit ;
- aucune conservation du mot de passe ou de son dérivé dans un cookie ;
- redirection après succès pour empêcher la resoumission.

Le mot de passe est limité à 32 caractères latins imprimables, car la clef aléatoire envoyée par le protocole Retro fait 32 caractères et le chiffrement historique traite un octet par caractère.

Le fil communautaire utilise le service `social-worker` et une API en lecture seule. Les quêtes terminées sont lues à la fois depuis la table historique et depuis `quest_progress`. La provenance exacte d’un objet n’étant pas conservée par le schéma Game, le fil indique qu’un personnage l’a « obtenu » sans affirmer qu’il s’agit d’un drop.

## Construction et paquet hors ligne

- Les images finales Game et Login sont reconstruites depuis les sources locales.
- Chaque reconstruction Java exécute `clean check jar` avec Gradle.
- La compilation hors ligne utilise le cache livré et interdit le réseau au conteneur de build.
- Le dépôt `starloco-docker` est épinglé au commit `44114333c8a900ad4ad3f64edfe1c03e603316c1`.
- `prepare-online.sh` vérifie l’URL `origin` et l’ascendance du commit existant sans reset d’une arborescence locale.
- Les fichiers factices nécessaires à la lecture du Compose amont sont créés dans un dossier temporaire ; aucun secret runtime n’est copié dans les sources.
- Les anciens services Compose orphelins sont supprimés au prochain start/stop, sans suppression des volumes.

La pile est conçue pour être pilotée par Docker Compose sur Ubuntu 24.04. Elle ne demande ni Java ni PHP sur l’hôte et n’est pas documentée comme une installation native hors Docker.

## Tests exécutés

### Compilation et contrôles statiques

- `clean check jar` réussi pour StarLoco Game ;
- `clean check jar` réussi pour StarLoco Login ;
- contrôles Java ajoutés pour `qping`, `BD`/`BT`, les trames enveloppées,
  les paquets tronqués et les adresses IPv4/IPv6 ;
- analyse syntaxique des scripts Bash ;
- validation de la configuration Docker Compose ;
- 21 tests PHP du portail, de la création de compte, des migrations et du fil communautaire : 21 réussis, 0 échec.

### Pile et base

- démarrage sur une pile fraîche : 6 services sains sur 6 ;
- `offline-doctor.sh --database` : 0 erreur, 0 avertissement ;
- migrations Game 05 à 09 présentes avec leurs checksums ;
- test sur un volume existant avec une ligne synthétique `quest_progress` : ligne préservée ;
- second `offline-start.sh` : migrations reconnues comme déjà appliquées et checksums vérifiés ;
- compte et personnage de test nettoyés : `accounts=0`, `players=0`.

### Smoke test réseau 1.41.9

Le test automatisé a vérifié :

1. le refus de `1.41.8e` avec annonce de la version attendue ;
2. l’acceptation de `1.41.9e` ;
3. l’authentification et les trames `AH`/`AxK` annonçant le monde `601` ;
4. le ticket Login → Game et la réponse `ATK0` ;
5. la création et la liste du personnage (`AA`/`AAK`/`ALK`) ;
6. sa sélection, l’inventaire et les sorts (`AS`/`ASK`, `Ow`, `SL`) ;
7. le chargement de la carte `10300` (`GC1`, `GCK`, `GDM`) ;
8. une reconnexion complète ;
9. la suppression (`AD`) et une liste finale vide (`ALK0`) ;
10. le nettoyage ciblé du compte temporaire.

Ce test passe par les sockets publiées et la base réelle de la pile, sans simuler les réponses du serveur.

## Limites connues

- Le client fourni est un exécutable Windows. Aucun lancement graphique sous Windows ou Wine n’a été réalisé dans cet audit.
- Le smoke test s’arrête après le chargement d’une carte et la reconnexion. Il ne couvre pas un combat complet.
- Les métiers, PNJ, quêtes, donjons, guildes, hôtels de vente, élevage, PvP et PvM n’ont pas chacun fait l’objet d’un scénario graphique exhaustif.
- Les fonctions d’interface étendue apparues dans la branche 1.41 — par exemple notes et annonces de guilde, nouveaux rangs, historique de combats, spectateur distant ou télémétrie associée — ne sont pas implémentées par ce socle StarLoco.
- Le format de mot de passe historique reste moins robuste qu’un hachage moderne avec facteur de coût. Le portail est destiné à un serveur local ou privé.
- Modifier les fichiers SQL après leur application est volontairement bloqué par les checksums ; une nouvelle migration doit être créée à la place.

## Commandes de reproduction

```bash
./offline-start.sh
./offline-doctor.sh --database
./offline-smoke-test.sh
./offline-verify.sh
```

Le premier démarrage ou toute mise à niveau importante doit être précédé d’une sauvegarde du volume MariaDB. La sauvegarde automatique interne à la migration protège spécifiquement `quest_progress`, pas l’ensemble de la base.
