# Carnet du Raider

Application Android compagnon pour **ARC Raiders**, à garder à côté du clavier pendant les sessions de jeu.

> Projet de fan, **non officiel**. ARC Raiders est une marque d'Embark Studios AB ; cette application
> n'est ni affiliée à Embark Studios ni approuvée par Embark Studios.

## Ce que fait l'appli

- **Garder, recycler ou vendre ?** Recherche d'un objet parmi tout le catalogue du jeu (sans tenir
  compte des accents), avec un verdict dès la liste : *Garder* (tes objectifs ou ta liste de raid en
  demandent), *Assez*, *Utile* (il sert à autre chose dans le jeu), ou *Vendre* / *Recycler*
  (comparaison en pièces) seulement s'il ne sert à rien. Un objet que tu cherches n'est jamais proposé
  à la vente.
- **À quoi sert chaque objet.** Sur sa fiche, avant de vendre : la description du jeu et tout ce qui le
  demande encore (fabrications, niveaux d'établis, quêtes, étapes de projets, clés, équipement, schémas
  à apprendre, Station de recherche, Avant-poste, échanges chez les commerçants).
- **Comment l'obtenir.** Sur chaque fiche : les meilleures cartes et zones où le trouver (d'après les
  tables de butin du jeu, avec un lien vers le plan), les commerçants et leurs prix, la fabrication, les
  objets qui le donnent au recyclage et les récompenses de quêtes et de projets.
- **Objectifs et progression automatique.** Tu choisis ce que tu veux faire avancer (« Suivre tous
  mes établis » en un geste) et tu indiques une fois tes quêtes en cours : l'appli suit d'elle-même le
  prochain niveau de chaque établi et les quêtes disponibles (d'après l'enchaînement des quêtes du
  jeu), ainsi que les projets que tu suis (Expéditions…). Chacun montre ce qui a déjà été rapporté ;
  dès qu'il a tout, il se termine tout seul et la suite apparaît. Un bouton valide aussi ce que tu as
  fini autrement (« Annuler » pour revenir en arrière). Des objectifs plus lointains peuvent s'ajouter
  à la main.
- **Préparer mon raid.** Rien à préparer : l'onglet Raid se remplit tout seul avec ce qui manque pour
  tes objectifs, plus ce que tu ajoutes toi-même (et combien). L'appli dit sur quelle carte aller, où
  chercher sur chacune et les cartes à faire pour tout trouver (d'après les tables de butin du jeu).
- **Retour de raid, sans réserve à tenir.** L'appli ne te demande jamais ce que tu as en stock : tu
  notes seulement ce que tu rapportes (« +1 », « +5 »), et seulement ce qu'il te faut. Chaque objet va
  aux objectifs qui le demandent (le plus près d'être terminé d'abord) et s'additionne d'un raid à
  l'autre. À la validation, ce qui est complet se termine tout seul, et une animation montre ce qui a
  été trouvé, puis « Raid terminé, objets trouvés » quand toute la liste est trouvée.
- **Conditions de la carte.** Le calendrier officiel d'Embark
  ([arcraiders.com/map-conditions](https://arcraiders.com/map-conditions)) dans l'appli, avec les noms
  français officiels : ce qui est en cours sur chaque carte, les prochaines heures et la prochaine fois
  de chaque condition, à l'heure de ton téléphone, pour ta région, avec un minuteur à la seconde. Gardé sur le téléphone, il reste
  lisible sans connexion. **Alertes** : une notification quelques minutes avant les conditions que tu
  suis, même appli fermée.
- **Plans des cartes.** Le plan en jeu de chaque carte, qu'on agrandit avec deux doigts, avec le nom
  officiel des lieux et des sorties, et les conditions en cours sur cette carte. Une recherche
  (« les trappes », « bondisseurs », un lieu…) ne montre que ça, et pour un ARC, ses points d'apparition.
- **Sauvegarde chiffrée.** Tes données sont sauvegardées automatiquement dans un fichier chiffré
  avec ton mot de passe, rangé où tu veux (Google Drive pour les retrouver sur un autre téléphone).
- **Sans compte.** Les données du jeu sont embarquées dans l'appli ; elle ne se connecte jamais à ton
  compte Embark ni aux serveurs du jeu, et ne touche jamais au jeu.
- **Mises à jour sécurisées depuis GitHub.** L'appli propose elle-même les nouvelles versions
  officielles, avec leurs nouveautés. Chacune est signée par la CI avec la clé de l'appli et vérifiée
  avant l'installation ([docs/MISES-A-JOUR.md](docs/MISES-A-JOUR.md)).

## Noms du jeu : uniquement les traductions françaises officielles

Tous les noms (objets, établis, quêtes, cartes, conditions…) sont ceux de la version française du jeu
(données de la version 2.0). Aucun nom n'est traduit par le projet : un nom dont la traduction
officielle n'est pas vérifiée est retiré de l'appli et listé dans
[docs/NOMS-A-VERIFIER.md](docs/NOMS-A-VERIFIER.md). Détails : [CLAUDE.md](CLAUDE.md).

## Installer

Depuis le téléphone, télécharger l'APK de la dernière version sur la page
[Releases](https://github.com/slaynAW/CarnetDuRaider/releases/latest), puis l'ouvrir. Android 8 ou plus
récent. Les versions suivantes s'installent ensuite depuis l'appli (*Réglages → Mises à jour*).

La pré-version `dev` et les artefacts des builds sont signés avec une clé de test : ils ne
s'installent pas par-dessus la version officielle ([docs/SIGNATURE.md](docs/SIGNATURE.md)).

## Publier une version

Compléter [NOUVEAUTES.md](NOUVEAUTES.md), puis **Actions → Build → Run workflow** avec le numéro
(ex. `0.2.0`) et valider la publication. Détails : [docs/MISES-A-JOUR.md](docs/MISES-A-JOUR.md) et,
pour la mise en place de la clé (une seule fois), [docs/SIGNATURE.md](docs/SIGNATURE.md).

## Développement

```bash
./gradlew :core:test                 # tests de la logique (données, liste de courses, verdict, mises à jour)
./gradlew :app:assembleDebug          # APK de debug
```

## Données du jeu

Le workflow « Données du jeu » (`.github/workflows/donnees.yml`) relance chaque jour l'import
(`scripts/importer_donnees_jeu.py`) sur les données publiées par raider-tools, puis les tests. Si
quelque chose a changé, il enregistre les nouvelles données sur `main` (fichiers de données
uniquement) et relance le build complet ; le résumé du workflow liste les nouveaux noms à vérifier en
jeu. Publier ensuite une version pour les recevoir sur le téléphone.

## Sources et licences

- Données du jeu : textes officiels et icônes des objets d'Embark Studios, extraits des fichiers du
  jeu par le projet communautaire
  [baschny/arcraiders-raider-tools](https://github.com/baschny/arcraiders-raider-tools)
  (`scripts/importer_donnees_jeu.py`). Tout le contenu du jeu est © Embark Studios AB.
- Polices : [Prompt](https://fonts.google.com/specimen/Prompt) et
  [Barlow](https://fonts.google.com/specimen/Barlow), SIL Open Font License 1.1
  ([third_party/polices](third_party/polices)).
