# Images Docker hors ligne

`starloco-images.tar` contient les images nécessaires au démarrage sans accès
réseau. `offline-load-images.sh` vérifie sa somme SHA-256 avant de la charger.

`prepare-online.sh` permet de reconstruire cette archive sur une machine
connectée après modification des sources ou changement des images de base.
