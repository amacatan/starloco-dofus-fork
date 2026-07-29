# Modifier et redéployer le serveur de jeu

Le code éditable du serveur vit directement dans :

```text
serveur-jeu/
```

Il reste dans le même dossier que les scripts Docker, la base et le portail. Les modifications de ce dossier sont volontairement exclues des sommes de contrôle du paquet afin que `offline-verify.sh` continue à vérifier l’infrastructure sans considérer votre propre code comme une corruption.

## Première initialisation

Sur une machine connectée :

```bash
./game-dev.sh init
```

La commande récupère exactement StarLoco Game `v1.0.6` et crée la branche locale `local-v1.0.6`. Elle ne remplace jamais un dépôt déjà présent et ne supprime pas les modifications locales.

Pour préparer en une seule fois toutes les images, les dépendances Gradle et les autres services :

```bash
./game-dev.sh bootstrap
```

## Cycle quotidien

1. Modifiez les fichiers Java dans `serveur-jeu/src/` et les scripts dans `serveur-jeu/scripts/`.
2. Vérifiez les changements :

```bash
./game-dev.sh diff
```

3. Testez et redéployez uniquement le serveur de jeu :

```bash
./game-dev.sh deploy
```

Cette commande :

- exécute `clean check jar` avec Gradle dans Docker et sans accès réseau ;
- construit l’image `starloco-offline/game:custom` ;
- enregistre la révision Git et l’état propre/modifié dans les labels de l’image ;
- recrée uniquement le conteneur `game`, sans toucher à MariaDB, Redis, Login ni au portail ;
- attend le healthcheck ;
- restaure automatiquement l’image précédente si le nouveau serveur ne devient pas sain.

## Commandes utiles

```bash
./game-dev.sh status      # Git, image, conteneur et dernière construction
./game-dev.sh test        # tests et compilation, sans déploiement
./game-dev.sh build       # image seulement
./game-dev.sh restart     # redémarrage sans compilation
./game-dev.sh logs        # journaux en direct
./game-dev.sh shell       # shell dans le conteneur
./game-dev.sh rollback    # retour manuel à l’image précédente
./game-dev.sh path        # chemin exact du code
```

## Données conservées

Le redéploiement du serveur de jeu ne recrée pas les volumes MariaDB et Redis. Les comptes, personnages, objets et autres données persistent. Une modification du schéma SQL doit cependant être livrée sous forme d’une migration dédiée et sauvegardée avant application.

## Git local

Le dossier `serveur-jeu/` est un dépôt Git normal. Vous pouvez créer vos propres commits et branches :

```bash
cd serveur-jeu
git switch -c ma-fonctionnalite
git add src scripts
git commit -m "Ajoute ma fonctionnalité"
```

Le fichier `runtime/builds/game-LAST_BUILD.txt`, généré après chaque compilation, indique précisément quelle révision a produit l’image active et si des fichiers non commités étaient présents.
