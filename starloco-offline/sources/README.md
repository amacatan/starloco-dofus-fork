# Sources éditables

Le serveur de jeu est désormais placé à la racine dans `../serveur-jeu/` pour simplifier l’édition et le redéploiement :

```bash
../game-dev.sh init
../game-dev.sh deploy
```

Ce dossier `sources/` conserve les autres composants récupérés par `../prepare-online.sh` :

- `StarLoco-Login` au tag `v1.0.3` ;
- `starloco-docker` au commit
  `44114333c8a900ad4ad3f64edfe1c03e603316c1`.

Le dossier historique `zaap/` peut encore être présent dans une ancienne copie du
paquet, mais il n'est ni récupéré, ni construit, ni lancé. Le « Zaap » de voyage
dans le jeu n'est pas concerné : seul l'ancien service HTTP externe a été retiré.

Consultez `../DEVELOPPEMENT-SERVEUR.md` pour le cycle complet du serveur de jeu.
