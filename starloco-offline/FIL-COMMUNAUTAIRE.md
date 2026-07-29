# Fil communautaire

Le portail web est désormais organisé autour de deux pages :

- `http://ADRESSE_DU_SERVEUR/` : fil public des aventuriers ;
- `http://ADRESSE_DU_SERVEUR/register.php` : création sécurisée d'un compte de jeu.

## Activités suivies

Le collecteur `social-worker` observe la base toutes les 15 secondes et publie :

- création de nouveaux personnages ;
- niveaux gagnés et niveaux importants ;
- objets remarquables ajoutés à l'inventaire ;
- quêtes terminées ;
- adhésions, départs et changements de guilde ;
- progression de rang d'alignement ;
- paliers de 100 victoires comptabilisées.

La première exécution constitue un état de référence sans publier artificiellement tous les personnages et objets déjà présents.

### À propos des objets

La base historique permet de constater qu'un objet est apparu dans l'inventaire, mais ne permet pas toujours de distinguer un drop d'un craft, d'un cadeau ou d'un échange. Le fil emploie donc la formulation exacte « a obtenu ». Les petits objets courants sont ignorés pour éviter le bruit.

Seuils réglables dans `runtime/stack/.env` :

```dotenv
SOCIAL_SCAN_INTERVAL=15
SOCIAL_ITEM_MIN_LEVEL=60
SOCIAL_ITEM_MIN_PRICE=50000
SOCIAL_ITEM_MIN_POINTS=1
SOCIAL_RETENTION_DAYS=90
SOCIAL_MAX_EVENTS=5000
```

Un objet est publié dès qu'il satisfait au moins un des trois seuils de niveau, prix moyen ou points boutique.

## Déployer la mise à jour sur une installation existante

Après avoir remplacé les fichiers du projet par ceux de cette version :

```bash
cd ~/starloco-corrige
./offline-enable-social-feed.sh
./offline-doctor.sh --database
```

Les volumes MariaDB et Redis ne sont pas supprimés : comptes, personnages et objets restent intacts.

## Vérifier le collecteur

```bash
./offline-status.sh
./offline-logs.sh social-worker
```

Dans les journaux, une activité détectée ressemble à :

```text
[social-worker] 12 personnage(s), 3 événement(s) créé(s)
```

Contrôle SQL direct :

```sql
SELECT id, event_type, player_name, title, happened_at
FROM website_social_events
ORDER BY id DESC
LIMIT 20;
```

## Vie privée

Le fil n'interroge pas `world_accounts` et n'affiche jamais :

- nom de compte ;
- adresse e-mail ;
- mot de passe ou hachage ;
- adresse IP ;
- question ou réponse secrète.

Seuls les noms de personnages et des informations déjà visibles ou déductibles dans le jeu sont publiés.
