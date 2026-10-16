# Consignes du projet Carnet du Raider

Application Android compagnon (non officielle) pour ARC Raiders. Tout le projet est en français :
interface, documentation, messages de commit, noms de classes et de fonctions métier.

## Règle absolue : uniquement les traductions françaises officielles d'Embark

Tout texte qui vient du jeu (cartes, objets, ARC, compétences, conditions de la carte, établis, quêtes,
commerçants, raretés, catégories…) doit être **la traduction française officielle d'Embark Studios**,
telle qu'elle apparaît dans le jeu.

- Ne jamais traduire, adapter ni « deviner » un nom du jeu, même s'il paraît évident.
- Les noms viennent uniquement de `core/src/main/resources/donnees-jeu.json`, produit par
  `scripts/importer_donnees_jeu.py` à partir des données du jeu. Ne pas modifier ce fichier à la main.
- Les icônes des objets (`app/src/main/assets/objets/<identifiant>.webp`) sont les icônes officielles
  du jeu, réduites par le même script (Pillow). Ne jamais en dessiner ni en ajouter à la main.
- Un nom sans traduction officielle vérifiée n'entre pas dans l'appli : le script l'exclut et le liste
  dans `docs/NOMS-A-VERIFIER.md`. Dans l'interface, un objet absent s'affiche « Nom à vérifier en jeu »
  (`Catalogue.NOM_A_VERIFIER`), jamais avec un nom inventé.
- Un nom identique en français et en anglais n'est accepté que s'il figure, avec sa justification,
  dans `scripts/noms-identiques-verifies.txt`.
- Les libellés fixes du code qui désignent un élément du jeu (raretés dans `Rarete`, « Atelier »,
  « Réserve », « Recyclage en », « Conditions de la carte »…) reprennent le texte officiel du jeu.
- Le reste du texte de l'interface (phrases d'aide, boutons propres à l'appli) est en français courant.

Mettre à jour les données après une mise à jour du jeu :

```bash
git clone --depth 1 https://github.com/baschny/arcraiders-raider-tools /tmp/raider-tools
python3 scripts/importer_donnees_jeu.py /tmp/raider-tools/public/data/game
./gradlew :core:test
```

Puis relire `docs/NOMS-A-VERIFIER.md` : chaque nouveau nom exclu est à vérifier dans le jeu en français.

## Typographie française

Espace insécable (` ` dans le code Kotlin) avant `:`, `?`, `!`, `%` et à l'intérieur des
guillemets `« … »`. Apostrophe droite `'` acceptée dans le code.

## Architecture

- `core/` : Kotlin pur, sans Android. Données du jeu (`DonneesJeu`, `Catalogue`), liste de courses
  (`Planificateur`) et verdict « garder, recycler ou vendre » (`Conseiller`). Toute logique métier va
  ici, avec ses tests JUnit (`./gradlew :core:test`).
- `app/` : interface Jetpack Compose, style « Signal » (`ui/theme/Theme.kt`) : fond sombre, et une
  couleur par domaine — or pour les objets, cyan pour les cartes, vert pour la progression, rouge pour
  les ARC. Polices Prompt (titres) et Barlow (texte), sous licence OFL (`third_party/polices/`).
  En-tête commun à toutes les sections (`EnTete` : logo, nom, réglages) ; objets affichés avec leur
  icône sur une tuile aux couleurs de leur rareté (`IconeObjet`).
- La réserve et la progression du joueur sont saisies dans l'appli (`Memoire`) : l'appli ne se connecte
  jamais au compte Embark et ne touche jamais au jeu (pas de lecture de mémoire, de trafic ou de jeton).
- Progression automatique (`core/Progression.kt`) : le joueur indique le niveau de ses établis et ses
  quêtes en cours ; l'appli en déduit les objectifs (prochain niveau, quêtes disponibles d'après
  l'enchaînement `requiert` des données du jeu) et retire de la réserve ce que le jeu a consommé.
- Calendrier des conditions de la carte (`core/…/calendrier`, `app/…/calendrier`) : lu sur la page
  officielle d'Embark (arcraiders.com/map-conditions, JSON embarqué dans la page, noms anglais), puis
  affiché avec les noms français officiels des données du jeu (correspondance par `cleCalendrier`).
  Un nom inconnu s'affiche « Nom à vérifier en jeu ». Le workflow « Calendrier officiel » vérifie
  chaque jour que la page est toujours lisible.
- Alertes (`core/…/calendrier/Alertes.kt`, `app/…/alertes`) : un seul réveil Android programmé à la
  fois (le prochain), calculé sur le calendrier gardé sur le téléphone ; reprogrammé au démarrage du
  téléphone, à la mise à jour de l'appli et quand le calendrier ou les réglages changent.
- Connexions de l'appli : uniquement GitHub (mises à jour) et arcraiders.com (calendrier). Jamais le
  compte Embark ni les serveurs du jeu, jamais de jeton de connexion du joueur.
- Mises à jour intégrées (`core/…/miseajour`, `app/…/miseajour`) : vers les Releases GitHub du dépôt,
  qui doit être **public** (sinon l'appli ne peut pas lire les fichiers publiés). Le manifeste
  `update.json` est signé par la CI avec la clé de l'APK et vérifié avec le certificat de l'appli
  installée ; l'APK doit avoir la taille et l'empreinte annoncées.
  Ne jamais affaiblir ces contrôles ni ajouter d'autre source de mise à jour (docs/MISES-A-JOUR.md).

## Publication et clé de signature

- La clé de signature n'est **jamais** dans le dépôt ni dans un job autre que « Publication
  officielle » (environnement protégé `publication`, secrets `CDR_KEYSTORE_*`). Voir docs/SIGNATURE.md.
- Une version officielle exige une section `## X.Y.Z` dans `NOUVEAUTES.md` (texte affiché dans
  l'appli) ; les noms du jeu y suivent la règle des traductions officielles.
- Actions épinglées par empreinte de commit, jeton en lecture seule par défaut.

## Vérifications

Le SDK Android n'est pas installable dans les sessions cloud (domaine bloqué) : la CI GitHub
(`.github/workflows/build.yml`) compile l'appli, lance le lint et produit l'APK. En session cloud, les
tests du module `core` peuvent tourner sur une copie du projet limitée à `core`, avec le miroir Maven
Central de Google (`https://maven-central.storage-download.googleapis.com/maven2/`).
