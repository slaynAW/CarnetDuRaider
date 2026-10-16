# Mises à jour intégrées

L'application propose elle-même les nouvelles versions officielles publiées sur GitHub, avec leurs
nouveautés, et s'installe en une touche. La réserve et les objectifs saisis sont conservés. Le
mécanisme est le même que celui de Patronus.

## Ce que voit l'utilisateur

- À l'ouverture de l'appli (au plus toutes les 12 h), elle regarde s'il existe une version plus
  récente. Si oui, une fenêtre **« Mise à jour disponible »** affiche le numéro, la date, la taille et
  les **nouveautés**, avec **Plus tard** et **Mettre à jour**. Un bandeau le rappelle sur l'accueil.
- *Plus tard* : la version n'est plus proposée d'elle-même pendant 24 h (le bandeau de l'accueil et
  *Réglages → Mises à jour* restent disponibles).
- *Réglages* (roue dentée de l'accueil) → **Rechercher une mise à jour** à tout moment, et l'option
  **Rechercher automatiquement** (activée par défaut).

L'application télécharge et vérifie l'APK puis le confie à l'installateur d'Android. Hors Play Store,
Android demande une confirmation : la première fois, il faut aussi autoriser *Carnet du Raider* à
installer des applications (*Paramètres → Installer des applis inconnues*). À partir d'Android 12, une
fois que l'application s'est mise à jour elle-même, les mises à jour suivantes peuvent se faire sans
confirmation. Une mise à jour conserve toujours les données de l'application (même clé de signature,
numéro de version plus grand).

## Publier une nouvelle version

1. Compléter **`NOUVEAUTES.md`** : une section `## X.Y.Z` (titres `###`, listes `-`, `**gras**`).
   C'est ce texte qui s'affiche dans l'application (et en tête de la Release GitHub). Les noms du jeu
   y suivent la règle des traductions officielles ([CLAUDE.md](../CLAUDE.md)).
2. Fusionner dans `main`, puis **Actions → Build → Run workflow** avec le numéro `X.Y.Z`
   (ou pousser un tag `vX.Y.Z`).
3. Valider la publication (**Review deployments** → `publication` → **Approve and deploy**,
   voir [SIGNATURE.md](SIGNATURE.md)).
4. La CI publie la Release avec l'APK et le manifeste **`update.json`** (+ `update.json.sig`).
   L'application le trouve à l'adresse
   `https://github.com/slaynAW/CarnetDuRaider/releases/latest/download/update.json`.
5. Passer `cdr.versionName` (`gradle.properties`) au numéro suivant : c'est celui des builds de
   développement (`X.Y.Z-dev.N`).

Seules les versions officielles sont proposées (la pré-version `dev` est ignorée). Le numéro comparé
est le code de build (numéro d'exécution de la CI), qui augmente à chaque build. La CI refuse de
publier un numéro déjà publié ou plus ancien que la dernière version.

## Sécurité

- `update.json` contient le numéro de version, le code de build, les nouveautés, et le nom, la taille
  et l'empreinte **SHA-256** de l'APK.
- Il est **signé avec la clé de signature de l'APK** (secrets `CDR_KEYSTORE_*`, RSA / SHA-256) par le
  job « Publication officielle » de la CI, le seul à recevoir la clé, après validation
  ([SIGNATURE.md](SIGNATURE.md)). Aucun autre secret n'est nécessaire.
- L'application vérifie la signature avec le **certificat de l'application installée** : un
  manifeste non signé, signé par une autre clé ou modifié est refusé
  (`core/…/miseajour/MisesAJour.kt`, testé dans `MiseAJourTest`).
- Le fichier téléchargé doit avoir exactement la taille et l'empreinte annoncées, sinon il est
  supprimé sans être installé. L'application vérifie ensuite que l'APK est bien *Carnet du Raider*
  dans la version annoncée, puis l'installateur d'Android vérifie qu'il est signé avec la même clé
  que l'application installée.
- Chaque fichier publié porte une **attestation de provenance** (Sigstore) :
  `gh attestation verify <fichier> --repo slaynAW/CarnetDuRaider`.
- La recherche de mises à jour contacte GitHub (en HTTPS). Elle ne transmet aucune donnée personnelle
  (ni réserve, ni objectifs, ni compte) et peut être désactivée.

## Dépôt public obligatoire

L'application lit `update.json` et l'APK sans compte GitHub : les fichiers des Releases doivent donc
être publics. **Le dépôt doit être public** ; dans un dépôt privé, l'application ne trouve jamais de
mise à jour (et l'attestation de provenance demande une offre GitHub payante). Rendre le dépôt public
n'expose rien de sensible : la clé de signature n'est que dans les secrets de l'environnement
`publication`, jamais dans le dépôt.

## Fichiers

| Fichier | Rôle |
|---|---|
| `core/src/main/kotlin/…/core/miseajour/MisesAJour.kt` | Vérification de la signature, lecture du manifeste, téléchargement contrôlé |
| `app/src/main/java/…/miseajour/GestionnaireMiseAJour.kt` | Recherche (12 h), « Plus tard » (24 h), installation via l'installateur d'Android |
| `app/src/main/java/…/ui/DialogueMiseAJour.kt`, `ui/EcranReglages.kt` | Fenêtre « Mise à jour disponible » et réglages |
| `.github/scripts/manifeste-mise-a-jour.sh` | Création et signature de `update.json` |
| `.github/scripts/signer-apk.sh` | Signature de l'APK avec la clé définitive, et contrôles |
| `.github/workflows/build.yml` | Jobs « Pré-version de développement » et « Publication officielle » |

## Dépannage

| Situation | Cause et solution |
|---|---|
| « Mise à jour refusée : sa signature ne correspond pas à cette appli » | APK de test (pré-version `dev` ou artefact d'un build) : installer une fois la version officielle depuis GitHub (désinstaller d'abord l'APK de test). |
| « Installation annulée. » | La confirmation d'Android a été refusée : relancer *Mettre à jour*. |
| « GitHub ne répond pas » | Pas de connexion, ou GitHub indisponible : réessayer plus tard. |
| Aucune proposition alors qu'une version existe | La Release ne contient pas `update.json`, ou elle est marquée « pré-version ». |
