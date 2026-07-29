# Client Dofus Retro 1.41.9

Ce guide concerne le client fourni dans `Retro-1.41.9` et la pile StarLoco de `starloco-offline`.

## Modification effectuée

Un seul fichier du client a été modifié :

```text
../Retro-1.41.9/Retro/resources/app/retroclient/config.xml
```

Le profil `En ligne` contient cette connexion directe :

```xml
<connserver name="StarLoco local" ip="127.0.0.1" port="450"/>
```

Les deux databanks locales `data/` ont été conservées avec leur priorité d’origine. Aucun exécutable, SWF, menu, manifeste ou autre fichier du client n’a été modifié.

Le `config.xml` conserve aussi les CDN Ankama comme solution de repli de
priorité inférieure. Le serveur StarLoco démarre entièrement hors ligne, mais le
client peut donc tenter un accès Internet si une ressource manque dans ses
databanks locales.

L’ancien attribut `zaapconnectport="26117"` a été retiré. Il désigne le protocole du launcher Ankama, que l’ancienne API HTTP Zaap de la pile n’implémentait pas. La connexion locale utilise directement le serveur Login sur `450`.

Ce changement n’a aucun rapport avec les Zaaps de téléportation présents dans le monde : ceux-ci restent des objets et mécanismes du serveur de jeu.

## Utilisation sur la même machine

1. Dans `starloco-offline`, démarrez et attendez la pile :

   ```bash
   ./offline-start.sh
   ```

2. Créez un compte sur `http://127.0.0.1/register.php` ou avec :

   ```bash
   ./offline-create-account.sh
   ```

3. Lancez le client fourni dans un environnement Windows compatible et choisissez `StarLoco local`. Le lancement sous Wine n’a pas été validé.

La release attendue par Login est `1.41.9`. Le suffixe `e` est accepté ; le smoke test utilise donc `1.41.9e`. Une autre release reçoit un refus annonçant `1.41.9`.

## Client sur une autre machine du LAN

Remplacez `127.0.0.1` dans le `connserver` par l’adresse LAN du serveur Ubuntu, par exemple :

```xml
<connserver name="StarLoco local" ip="192.168.1.20" port="450"/>
```

Dans `starloco-offline/runtime/stack/.env`, utilisez la même adresse :

```text
BIND_ADDRESS=192.168.1.20
GAME_SERVER_IP=192.168.1.20
```

Puis relancez :

```bash
./offline-start.sh
```

`GAME_SERVER_IP` est important : Login transmet cette adresse au client lorsqu’il l’envoie vers le serveur Game.

## Ports

| Port TCP | Usage | À ouvrir pour un client distant |
|---|---|---|
| `450` | authentification Login | oui |
| `5555` | serveur Game | oui |
| `80` | portail d’inscription | seulement si souhaité |
| `666` | échange Login/Game dans Docker | non |

MariaDB et Redis ne doivent pas être exposés. Aucun port `8000` ou `26117` n’est requis par cette configuration directe.

## Vérification sans interface graphique

Après le démarrage :

```bash
./offline-doctor.sh --database
./offline-smoke-test.sh
```

Le smoke test réseau validé avec `1.41.9e` couvre :

- le refus de `1.41.8e` ;
- l’authentification ;
- la découverte du monde `601` ;
- le ticket vers Game ;
- la création, la liste et la sélection d’un personnage ;
- l’inventaire et la liste de sorts ;
- le chargement de la carte `10300` ;
- une reconnexion ;
- la suppression du personnage et le nettoyage du compte temporaire.

## Dépannage

Si le client ne voit pas Login :

```bash
./offline-status.sh
./offline-logs.sh login
```

Vérifiez l’adresse du `connserver`, `BIND_ADDRESS` et le pare-feu du serveur. Si Login fonctionne mais que la connexion au monde échoue, vérifiez `GAME_SERVER_IP`, le port `5555` et les journaux Game :

```bash
./offline-logs.sh game
```

Le diagnostic complet de la pile est :

```bash
./offline-doctor.sh --database
```

## Limites de validation

Le client fourni est un exécutable Windows (`Dofus Retro.exe`). Il n’a pas été lancé graphiquement pendant cet audit, ni sous Windows ni sous Wine. Aucune garantie spécifique à Wine n’est donc donnée.

Le test automatisé valide le protocole jusqu’au chargement d’une carte et à la reconnexion, pas un combat complet. Les extensions d’interface propres aux versions 1.41, notamment les notes et annonces de guilde, les nouveaux rangs, l’historique de combats, le spectateur distant et la télémétrie associée, ne sont pas implémentées par ce serveur.

Les fonctions classiques non parcourues par le smoke test n’ont pas été validées individuellement dans l’interface 1.41.9 ; aucune conclusion supplémentaire n’est tirée pour elles dans ce guide.
