# Inventaire des bugs — 5 septembre 2026

Audit du dépôt à la révision `85048334`, avec les modifications locales présentes au moment de la revue. Mise à jour du 6 septembre 2026 : le défaut de chargement B01 et les dialogues d'entrée B02 et de sortie B03 sont corrigés dans les sources et le contexte de construction Game. Les autres constats restent ouverts.

Huit problèmes ont été retenus lors de l'audit initial : cinq défauts de dialogue reproduits avec le moteur Lua du projet, un ensemble de sorties de cartes invalides vérifié statiquement et deux défauts de l'outillage reproduits localement. Cet inventaire n'est pas exhaustif.

## Priorités

P1 : parcours de jeu bloqué, à traiter en premier. P2 : fonctionnalité locale ou outillage défectueux.

| ID | Priorité | Problème | Validation |
|---|---|---|---|
| B01 | P1 | **Chargement corrigé** : 44 sorties visaient 34 cartes absentes ; aucune destination directe absente désormais | 9 126 cartes, 23 647 sorties ; chargement réel des 60 cartes restaurées |
| B02 | P1 | **Corrigé** : Ziho répond à l'entrée du sanctuaire des Dragoeufs | Exécution Lua avant/après, avec et sans clé ou trousseau |
| B03 | P1 | **Corrigé** : Ziho permet de quitter la salle de sortie | Exécution Lua avant/après : confirmation, téléportation et fermeture du dialogue |
| B04 | P1 | Le dialogue d'échange des doplons en parchemins n'aboutit pas | Exécution Lua |
| B05 | P2 | Une branche de dialogue de Gropinson appelle une méthode inexistante | Exécution Lua |
| B06 | P2 | Acidrik propose une réponse sans suite | Exécution Lua |
| B07 | P2 | Les builds annoncent une révision et un état Git incorrects | Exécution des fonctions de métadonnées |
| B08 | P2 | `game-dev.sh init` refuse les sources déjà présentes dans ce dépôt | Exécution de la commande : code de sortie 1 |

## B01 — Sorties vers des cartes non chargées — corrigé le 6 septembre

**Effet initial :** emprunter une sortie concernée demandait une carte dont le serveur ne possédait aucune définition active. Le changement de carte ne pouvait pas aboutir.

Le croisement initial des appels directs de téléportation avec les définitions de `scripts/data/maps` donnait **44 sorties vers 34 identifiants absents**. Les scripts du dossier `data/notloaded` avaient été exclus des sources contrôlées. Aucun dépassement du nombre de cellules n'avait été trouvé dans les destinations directes contrôlées.

Le chargeur [Data.lua](serveur-jeu/scripts/Data.lua), lignes 33 et 45, enregistre les cartes actives. [World.java](serveur-jeu/src/org/starloco/locos/game/world/World.java), ligne 210, lève une exception si une carte demandée n'a pas de définition ; il n'existe pas de repli SQL dans ce chemin.

Exemples du défaut initial :

- Cania : les cartes 4114, 4117, 4109 et 4112 pointent vers 4113.
- Koalaks : les cartes 8986 et 8850 pointent vers 8985.
- Labyrinthe du Dragon Cochon : les quatre sorties de la carte 9395 pointent vers des cartes absentes.

**Correction appliquée :**

- Deux identifiants erronés corrigés à partir de `scripted_cells` et des liaisons de retour : carte 565, cellule 131, vers **566,129** ; carte 7288, cellule 401, vers **8269,207**.
- **60 cartes activées** : 31 définitions reprises de `scripts/data/notloaded/maps`, et 29 restaurées depuis les tables `maps`, `npcs`, `monsters` et `scripted_cells` de `db-init/04-game.sql`. Les cartes supplémentaires ferment les liaisons introduites par les premières restaurations, dont les 24 salles 9371–9394 du labyrinthe du Dragon Cochon et les grottes 4175–4176 de Cania.
- Pour la carte 9378, retrait du seul caractère final incomplet de `places`, tronqué par l'ancien champ SQL `varchar(300)`. Toutes les paires complètes de cellules de placement sont conservées ; le chargeur Java accepte maintenant cette définition.
- Même contenu dans `serveur-jeu/scripts` et `build-contexts/game/scripts`, avec mise à jour des sommes SHA-256 du paquet.
- [MapLuaChecks.java](serveur-jeu/test/org/starloco/locos/script/MapLuaChecks.java) vérifie désormais l'unicité des identifiants, l'existence des destinations et les bornes des cellules de départ et d'arrivée. [RestoredMapChecks.java](serveur-jeu/test/org/starloco/locos/script/RestoredMapChecks.java) exécute les 60 scripts avec Luna, déchiffre les données, construit les véritables `ScriptMapData`, vérifie les placements de combat et de PNJ, et compare les copies de construction.

**Résultat vérifié :** 9 126 cartes actives, 23 647 sorties directes, **zéro destination absente** et aucun indice de cellule hors limites. Compilation de l'ensemble des sources et tests Game avec Java 21, puis `GameUnitChecks` : réussite. Les 60 fichiers SWF correspondant aux identifiants et révisions restaurés existent dans les ressources montées par le service web.

**Limite distincte restant à traiter :** les données historiques de quatre cartes contiennent des cellules non praticables selon `active` et `movement`. Huit liaisons arrivent sur ces cellules ; huit déclencheurs de sortie des cartes restaurées sont eux-mêmes non praticables. Ces valeurs étaient déjà présentes dans les archives ; la restauration les conserve. Le contrôle des bornes ne garantit donc pas que tous ces passages soient utilisables dans le client.

| Carte | Cellules d'arrivée non praticables | Déclencheurs de sortie non praticables |
|---|---|---|
| 11745 | 114 (inactive) | — |
| 13063 | 455 | 276, 470 |
| 13066 | 31, 291, 361, 438 | 17, 305, 347, 452 |
| 13067 | 378, 439 | 392, 453 |

Le tracé de ces passages reste à vérifier dans le client avant de déplacer leurs cellules. Les cartes 13063, 13066 et 13067 utilisent des géométries de cartes personnalisées ; les archives SQL reprennent les mêmes liaisons et ne permettent pas de déduire une autre destination certaine.

La liste initiale des 44 sorties figure en annexe. Les chemins indirects, le parcours graphique et le démarrage de la pile complète n'ont pas été validés ; aucun serveur en cours d'exécution n'a été redémarré pour cette correction.

## B02 — Ziho ignore sa carte d'entrée — corrigé le 6 septembre

**Source :** [789_Ziho.lua](serveur-jeu/scripts/data/npcs/dungeons/789_Ziho.lua), ligne 12.

Le dialogue d'entrée était conditionné à la carte **9638**, alors que Ziho est placé sur la carte **7858** dans [la définition de l'entrée](serveur-jeu/scripts/data/maps/dungeons/Dreggon/7858_Dreggon_Sanctuary_Entrance.lua), ligne 18.

**Constat initial :** appeler le dialogue sur sa carte réelle ne produisait aucune question. L'accès par ce PNJ restait inactif.

**Correction appliquée :** filtre de carte aligné sur **7858** dans le script source et sa copie de construction ; sommes SHA-256 mises à jour.

**Validation :** défaut reproduit avant correction, puis exécution des scripts réels avec Luna et un joueur simulé. La question 3236 est affichée à l'entrée ; la réponse d'entrée est proposée avec une clé ou un trousseau compatible et mène à la carte 9822, cellule 89, en fermant le dialogue. Sans clé ni trousseau, seules les réponses informatives sont proposées ; leurs treize étapes ont été parcourues. Le dialogue d'entrée ne se déclenche plus sur la carte 9638. Le défaut distinct de sortie est traité sous B03.

## B03 — Ziho répète la question de sortie — corrigé le 6 septembre

**Source :** [789_Ziho.lua](serveur-jeu/scripts/data/npcs/dungeons/789_Ziho.lua), lignes 33–40.

Sur la carte **10110**, la branche qui affichait la question s'exécutait quelle que soit la réponse. La branche suivante, qui devait traiter le départ, n'était donc jamais atteinte sur cette carte.

**Constat initial :** demander à sortir réaffichait la question ; aucune téléportation n'était déclenchée. Le PNJ est bien placé dans [la salle de sortie](serveur-jeu/scripts/data/maps/dungeons/Dreggon/10110_Dreggon_Exit.lua), ligne 16.

**Correction appliquée :** sur la carte 10110, la question 3253 est affichée uniquement à l'ouverture du dialogue (`answer == 0`). La réponse 2864 téléporte à la carte **7858, cellule 312**, puis ferme le dialogue. La confirmation de départ est désormais traitée à l'intérieur de la branche de la salle de sortie. Script source et copie de construction synchronisés, sommes SHA-256 mises à jour.

**Validation :** défaut reproduit avant correction, puis parcours complet avec Luna, les scripts réels et un joueur simulé sans clé. La confirmation produit une seule téléportation et une seule fermeture, sans répéter la question. Une nouvelle confirmation après le départ, une confirmation depuis une autre carte et les réponses inconnues ne déclenchent aucune action. Les parcours d'entrée B02 ont également été revérifiés avec et sans clé ou trousseau. Aucun parcours dans le client graphique ni redémarrage du serveur n'a été effectué.

## B04 — Échange des doplons inachevé

**Source :** [Dopples.lua](serveur-jeu/scripts/data/Dopples.lua), lignes 217–233.

Le menu permet de choisir la taille du parchemin, la caractéristique et le type de paiement. Il affiche les deux réponses finales, mais aucune branche ne les traite.

**Constat :** le parcours jusqu'à la dernière question fonctionne ; sélectionner l'une ou l'autre option finale ne donne aucun objet et ne poursuit pas le dialogue.

**Correction à prévoir :** implémenter la validation finale et la transaction de l'échange. Le constat porte sur ce dialogue ; les offres de vente déclarées séparément dans `npc.sales` ne sont pas visées.

## B05 — Erreur de méthode chez Gropinson

**Source :** [151_Gropinson_Cruaule.lua](serveur-jeu/scripts/data/npcs/moon/151_Gropinson_Cruaule.lua), ligne 10.

Une réponse appelle `p:aks(570)` au lieu de `p:ask(570)`. La méthode `aks` n'existe pas dans l'API du joueur.

**Constat :** le moteur Lua produit `attempt to call a nil value` ; cette branche du dialogue s'interrompt. Gropinson est placé sur la carte 425, à Moon.

**Correction à prévoir :** corriger le nom de méthode.

## B06 — Réponse sans traitement chez Acidrik

**Source :** [729_Acidrik_Gutsplitter_q127.lua](serveur-jeu/scripts/data/npcs/astrub/729_Acidrik_Gutsplitter_q127.lua), lignes 10–14.

Le dialogue affiche la question 2983 avec une réponse proposée, mais ne contient aucun traitement de cette réponse.

**Constat :** après les deux premières questions, la suite ne produit aucune action. Acidrik est placé sur la carte 7573, dans Astrub. La quête 127 est également indiquée comme inachevée dans le script.

**Correction à prévoir :** compléter le dialogue et son raccordement à la quête.

## B07 — Métadonnées de build erronées dans le dépôt actuel

**Source :** [offline-rebuild.sh](offline-rebuild.sh), lignes 54–69.

Les fonctions `source_revision` et `source_dirty` exigent un dossier `.git` directement dans les sources Game ou Login. Dans le dépôt actuel, ces sources appartiennent au dépôt parent.

**Constat :** pour Game, les fonctions renvoient `sans-git` et `false`, alors que Git retrouve le dépôt parent et que des modifications Game sont présentes. Les labels d'image et le fichier de dernière construction héritent de ces valeurs erronées.

**Correction à prévoir :** détecter le dépôt avec Git, puis limiter la recherche des modifications au sous-dossier concerné.

## B08 — Initialisation incompatible avec les sources intégrées au dépôt

**Source :** [game-dev.sh](game-dev.sh), lignes 50–61.

La commande considère les sources comme déjà initialisées uniquement si `serveur-jeu/.git` existe. Sinon, elle exige un dossier vide. Les sources intégrées au dépôt parent ne satisfont aucune de ces conditions.

**Constat :** `bash starloco-offline/game-dev.sh init` termine avec le code 1 et le message « serveur-jeu doit être vide avant l'initialisation ».

**Correction à prévoir :** reconnaître les sources déjà présentes et suivies par le dépôt parent. Le défaut affecte la commande d'initialisation documentée, pas la compilation déjà vérifiée.

## Vérifications et limites

- Sources et tests Game et Login recompilés avec Java 21 dans un dossier temporaire, à partir des dépendances locales.
- `GameUnitChecks` et `LoginUnitChecks` : réussite.
- Contrôles du workflow développeur : réussite.
- Cinq comportements de dialogue vérifiés en exécutant les scripts réels avec Luna, avec des joueurs et services simulés.
- Deux défauts d'outillage reproduits ; analyse statique des destinations directes de cartes.
- Tests PHP, base réelle, parcours réseau et client graphique non exécutés : PHP n'est pas installé sur l'hôte et l'accès au démon Docker n'était pas disponible dans l'environnement d'audit.

Les erreurs volontairement provoquées par les tests existants ne sont pas comptées comme bugs. Les TODO isolés et les anomalies sans parcours actif établi ne sont pas inclus dans les huit constats.

## Annexe — Les 44 sorties initialement dirigées vers des cartes absentes

État observé le 5 septembre, conservé pour la traçabilité. Depuis la correction B01, les destinations sont actives ou leur identifiant a été corrigé (565 → 566 et 7288 → 8269).

| Fichier source | Ligne | Carte destination absente | Cellule |
|---|---:|---:|---:|
| [cania/4114_-3_-44.lua](serveur-jeu/scripts/data/maps/cania/4114_-3_-44.lua) | 71 | 4113 | 442 |
| [cania/4117_-2_-45.lua](serveur-jeu/scripts/data/maps/cania/4117_-2_-45.lua) | 72 | 4113 | 216 |
| [cania/4799_-12_-61.lua](serveur-jeu/scripts/data/maps/cania/4799_-12_-61.lua) | 46 | 4803 | 441 |
| [cania/4800_-11_-62.lua](serveur-jeu/scripts/data/maps/cania/4800_-11_-62.lua) | 41 | 4803 | 216 |
| [cania/4109_-4_-45.lua](serveur-jeu/scripts/data/maps/cania/4109_-4_-45.lua) | 43 | 4113 | 233 |
| [cania/4112_-3_-46.lua](serveur-jeu/scripts/data/maps/cania/4112_-3_-46.lua) | 44 | 4113 | 35 |
| [cania/4853_-13_-62.lua](serveur-jeu/scripts/data/maps/cania/4853_-13_-62.lua) | 52 | 4803 | 233 |
| [koalaks/8986_-20_-3.lua](serveur-jeu/scripts/data/maps/koalaks/8986_-20_-3.lua) | 43 | 8985 | 303 |
| [koalaks/8850_-21_-2.lua](serveur-jeu/scripts/data/maps/koalaks/8850_-21_-2.lua) | 36 | 8985 | 443 |
| [amakna/7288_amakna_tunnel_-6_5.lua](serveur-jeu/scripts/data/maps/amakna/7288_amakna_tunnel_-6_5.lua) | 17 | 8267 | 207 |
| [amakna/565_amakna_10_11.lua](serveur-jeu/scripts/data/maps/amakna/565_amakna_10_11.lua) | 48 | 481 | 129 |
| [bonta/11805_-38_-55.lua](serveur-jeu/scripts/data/maps/bonta/11805_-38_-55.lua) | 20 | 11804 | 109 |
| [brakmar/11616_-30_38.lua](serveur-jeu/scripts/data/maps/brakmar/11616_-30_38.lua) | 20 | 11617 | 107 |
| [brakmar/11744_-22_41.lua](serveur-jeu/scripts/data/maps/brakmar/11744_-22_41.lua) | 20 | 11745 | 114 |
| [brakmar/11725_-24_37.lua](serveur-jeu/scripts/data/maps/brakmar/11725_-24_37.lua) | 20 | 11726 | 93 |
| [brakmar/11644_-28_36.lua](serveur-jeu/scripts/data/maps/brakmar/11644_-28_36.lua) | 20 | 11645 | 157 |
| [unsorted/1903_17_25.lua](serveur-jeu/scripts/data/maps/unsorted/1903_17_25.lua) | 46 | 1938 | 342 |
| [unsorted/1903_17_25.lua](serveur-jeu/scripts/data/maps/unsorted/1903_17_25.lua) | 48 | 1937 | 266 |
| [unsorted/7972_21_-37.lua](serveur-jeu/scripts/data/maps/unsorted/7972_21_-37.lua) | 40 | 8415 | 123 |
| [unsorted/13065_5_6.lua](serveur-jeu/scripts/data/maps/unsorted/13065_5_6.lua) | 21 | 13066 | 361 |
| [unsorted/8413_23_-33.lua](serveur-jeu/scripts/data/maps/unsorted/8413_23_-33.lua) | 20 | 8408 | 164 |
| [unsorted/9398_-1_33.lua](serveur-jeu/scripts/data/maps/unsorted/9398_-1_33.lua) | 21 | 9379 | 385 |
| [unsorted/9398_-1_33.lua](serveur-jeu/scripts/data/maps/unsorted/9398_-1_33.lua) | 22 | 9378 | 244 |
| [unsorted/9398_-1_33.lua](serveur-jeu/scripts/data/maps/unsorted/9398_-1_33.lua) | 23 | 9371 | 234 |
| [unsorted/9398_-1_33.lua](serveur-jeu/scripts/data/maps/unsorted/9398_-1_33.lua) | 24 | 9374 | 79 |
| [unsorted/13069_-20_10.lua](serveur-jeu/scripts/data/maps/unsorted/13069_-20_10.lua) | 21 | 13066 | 438 |
| [unsorted/6826_-9_-7.lua](serveur-jeu/scripts/data/maps/unsorted/6826_-9_-7.lua) | 21 | 6824 | 314 |
| [unsorted/8001_23_-34.lua](serveur-jeu/scripts/data/maps/unsorted/8001_23_-34.lua) | 38 | 8442 | 214 |
| [unsorted/8001_23_-34.lua](serveur-jeu/scripts/data/maps/unsorted/8001_23_-34.lua) | 39 | 8440 | 195 |
| [unsorted/13070_-41_-17.lua](serveur-jeu/scripts/data/maps/unsorted/13070_-41_-17.lua) | 21 | 13067 | 378 |
| [unsorted/6825_-9_-7.lua](serveur-jeu/scripts/data/maps/unsorted/6825_-9_-7.lua) | 21 | 6824 | 311 |
| [unsorted/9664_-16_18.lua](serveur-jeu/scripts/data/maps/unsorted/9664_-16_18.lua) | 20 | 11859 | 323 |
| [unsorted/1908_13_27.lua](serveur-jeu/scripts/data/maps/unsorted/1908_13_27.lua) | 36 | 1967 | 339 |
| [unsorted/1908_13_27.lua](serveur-jeu/scripts/data/maps/unsorted/1908_13_27.lua) | 37 | 1966 | 369 |
| [unsorted/13039_-59_15.lua](serveur-jeu/scripts/data/maps/unsorted/13039_-59_15.lua) | 21 | 13063 | 455 |
| [unsorted/13072_18_-36.lua](serveur-jeu/scripts/data/maps/unsorted/13072_18_-36.lua) | 23 | 13066 | 291 |
| [unsorted/13064_5_6.lua](serveur-jeu/scripts/data/maps/unsorted/13064_5_6.lua) | 23 | 13063 | 247 |
| [unsorted/8414_23_-33.lua](serveur-jeu/scripts/data/maps/unsorted/8414_23_-33.lua) | 20 | 8419 | 133 |
| [unsorted/7973_21_-36.lua](serveur-jeu/scripts/data/maps/unsorted/7973_21_-36.lua) | 32 | 8430 | 117 |
| [unsorted/7973_21_-36.lua](serveur-jeu/scripts/data/maps/unsorted/7973_21_-36.lua) | 33 | 8423 | 123 |
| [dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua](serveur-jeu/scripts/data/maps/dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua) | 20 | 9375 | 385 |
| [dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua](serveur-jeu/scripts/data/maps/dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua) | 21 | 9381 | 186 |
| [dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua](serveur-jeu/scripts/data/maps/dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua) | 22 | 9377 | 64 |
| [dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua](serveur-jeu/scripts/data/maps/dungeons/DragonPig/9395_DragonPig_Maze_Exit.lua) | 23 | 9387 | 292 |
