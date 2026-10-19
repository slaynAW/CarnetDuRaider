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
  De même pour les plans des cartes (`app/src/main/assets/cartes/<carte>.webp`, assemblés à partir des
  tuiles du plan en jeu de `public/data/map-data`), leurs lieux, sorties et points d'apparition des ARC
  (`Carte.plan`, noms français officiels de `map-strings.fr.json`). La recherche sur le plan
  (`RecherchePlan`) n'affiche que ces noms ; les mots de famille (« trappe », « ascenseur », « arc »…)
  servent seulement à chercher, jamais de nom affiché.
- Comment obtenir un objet (`Objet.obtention`, produit par le script) : meilleures cartes et zones (portage
  du calcul de raider-tools sur les tables de butin, condition normale, indice de 1 à 100), achats,
  fabrications, récompenses ; les sources de recyclage sont calculées dans `Catalogue`. Les vrais taux de
  drop ne sont pas dans les fichiers du jeu (tirage côté serveur) : ne jamais présenter l'indice comme un
  pourcentage ni comme un taux de drop.
- À quoi sert un objet : sa description officielle (`Objet.description`, gardée seulement si le jeu la
  traduit) et `Objet.utilisations` (fabrications, recherches, Avant-poste, échanges, amplification,
  schémas, clés, équipement), produits par le script ; les établis, quêtes et projets qui le demandent
  sont calculés dans `Usages`.
- Un nom sans traduction officielle vérifiée n'entre pas dans l'appli : le script l'exclut et le liste
  dans `docs/NOMS-A-VERIFIER.md`. Dans l'interface, un objet absent s'affiche « Nom à vérifier en jeu »
  (`Catalogue.NOM_A_VERIFIER`), jamais avec un nom inventé.
- Un nom identique en français et en anglais n'est accepté que s'il figure, avec sa justification,
  dans `scripts/noms-identiques-verifies.txt`.
- Les libellés fixes du code qui désignent un élément du jeu (raretés dans `Rarete`, « Atelier »,
  « Réserve », « Recyclage en », « Conditions de la carte »…) reprennent le texte officiel du jeu.
- Le reste du texte de l'interface (phrases d'aide, boutons propres à l'appli) est en français courant.

Les données sont mises à jour **chaque jour** par le workflow « Données du jeu »
(`.github/workflows/donnees.yml`) : import en lecture seule, tests du module core, puis commit sur
`main` des seuls fichiers de données (refus de tout autre fichier) et relance du build. Bibliothèques
Python de l'import à versions et empreintes fixées (`scripts/requirements-import.txt`). À la main :

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

- `core/` : Kotlin pur, sans Android. Données du jeu (`DonneesJeu`, `Catalogue`), liste de courses et
  objets à trouver (`Planificateur`), à quoi sert un objet (`Usages`) et verdict « garder, recycler ou
  vendre » (`Conseiller`). Toute logique métier va ici, avec ses tests JUnit (`./gradlew :core:test`).
- Verdict (`core/Verdict.kt`) : un objet voulu (objectifs suivis, ou ajouté par le joueur dans l'onglet
  Raid) n'est **jamais proposé à la vente** ; un objet qui sert encore à quelque chose (`Usages`) est
  « Utile », avec le détail sur sa fiche (« À quoi il sert ») ; seul un objet qui ne sert à rien est à
  vendre ou à recycler. Le verdict s'affiche aussi dans la liste des objets.
- `app/` : interface Jetpack Compose, style « Signal » (`ui/theme/Theme.kt`) : fond sombre, et une
  couleur par domaine — or pour les objets, cyan pour les cartes, vert pour les objectifs, rouge pour
  le raid et les ARC. Cinq onglets : Accueil, Objets, Raid, Cartes, Objectifs ; le joueur doit en faire
  le moins possible (rien à préparer, un geste pour valider ou noter un objet trouvé). Polices Prompt (titres) et Barlow (texte), sous licence OFL (`third_party/polices/`).
  En-tête commun à toutes les sections (`EnTete` : logo, nom, réglages) ; objets affichés avec leur
  icône sur une tuile aux couleurs de leur rareté (`IconeObjet`).
- La réserve et la progression du joueur sont saisies dans l'appli (`Memoire`) : l'appli ne se connecte
  jamais au compte Embark et ne touche jamais au jeu (pas de lecture de mémoire, de trafic ou de jeton).
  **À l'installation, tout est à zéro** : aucun établi suivi (`Progression.etablisSuivis`, à choisir),
  aucune quête suivie avant que le joueur les indique (`quetesIndiquees`), aucun projet.
- Sauvegarde chiffrée (`core/…/sauvegarde`, `app/…/sauvegarde`) : fichiers de réglages « carnet » et
  « alertes » écrits automatiquement (quelques secondes après chaque changement) dans un fichier choisi
  par le joueur avec le sélecteur d'Android (Google Drive, dossier du téléphone). PBKDF2-SHA256
  (600 000 tours) + AES-256-GCM, en-tête authentifié ; mot de passe jamais enregistré, clé dérivée gardée
  chiffrée par le Keystore d'Android. L'appli n'ouvre aucune connexion pour cela. Pas de sauvegarde
  sur GitHub : écrire sur GitHub exige un jeton, qu'on ne peut pas mettre dans un APK public.
- Progression automatique (`core/Progression.kt`) : le joueur indique le niveau de ses établis et ses
  quêtes en cours ; l'appli en déduit les objectifs (prochain niveau, quêtes disponibles d'après
  l'enchaînement `requiert` des données du jeu) et retire de la réserve ce que le jeu a consommé.
  Les projets suivis (Expéditions…) ajoutent leur prochaine étape. Un niveau, une quête ou une étape se
  valide en un geste (`CarnetViewModel.valider…`), avec « Annuler » : jamais de saisie obligatoire.
- Onglet Raid (`core/PreparationRaid.kt`, `ui/EcranRaid.kt`) : rien à préparer. Ce qu'il faut trouver
  (`Planificateur.aTrouver`) = ce qui manque aux objectifs suivis, puis les objets ajoutés par le joueur
  (`Memoire`, clé « raid ») ; cartes classées par la somme des indices de butin des objets encore à
  trouver (chaque objet compte au plus 1 par carte), zones où chercher, et parcours de 1 à 3 cartes
  pour tout trouver. « +1 » note un objet trouvé ; l'accueil reprend la meilleure carte.
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

## Aperçu web (à tenir à jour)

`apercu/` contient une version web de l'appli (mêmes données, mêmes règles que `core/`, réécrites en
JavaScript dans `apercu/modele.html`), publiée comme artifact claude.ai :
https://claude.ai/artifact/5pwyAtzHArvPaTgxtnU3ek. **À chaque fonction ajoutée ou modifiée dans
l'appli, reporter le changement dans `apercu/modele.html`**, puis `python3 apercu/construire.py`
et republier `apercu/index.html` sur la même URL, avec en fichiers joints `apercu/icones.json` et les
plans `apercu/cartes/<carte>.webp`. Ce qui
ne peut pas tourner dans une page web (calendrier en direct, alertes, mises à jour) y est signalé
comme propre à l'appli Android.

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
