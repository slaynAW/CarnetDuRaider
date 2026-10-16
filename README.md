# Carnet du Raider

Application Android compagnon pour **ARC Raiders**, à garder à côté du clavier pendant les sessions de jeu.

> Projet de fan, **non officiel**. ARC Raiders est une marque d'Embark Studios AB ; cette application
> n'est ni affiliée à Embark Studios ni approuvée par Embark Studios.

## Ce que fait l'appli

- **Garder, recycler ou vendre ?** Recherche d'un objet parmi tout le catalogue du jeu (sans tenir
  compte des accents), puis une fiche avec un verdict selon *tes* objectifs : ce qu'il t'en manque, ou,
  s'il est inutile, s'il vaut mieux le vendre ou le recycler (comparaison en pièces).
- **Progression automatique.** Tu indiques une fois le niveau de tes établis et tes quêtes en cours :
  l'appli suit d'elle-même le prochain niveau de chaque établi et les quêtes disponibles (d'après
  l'enchaînement des quêtes du jeu). Quand tu coches une amélioration ou une quête terminée, la suite
  apparaît et ce que le jeu a pris est retiré de ta réserve.
- **Liste de courses.** Les objets à trouver pour tout ça, avec ta réserve mise à jour en une touche
  (« − » / « + »). Des objectifs plus lointains peuvent s'ajouter à la main.
- **Conditions de la carte.** Le calendrier officiel d'Embark
  ([arcraiders.com/map-conditions](https://arcraiders.com/map-conditions)) dans l'appli, avec les noms
  français officiels : ce qui est en cours sur chaque carte, les prochaines heures et la prochaine fois
  de chaque condition, à l'heure de ton téléphone, pour ta région. Gardé sur le téléphone, il reste
  lisible sans connexion.
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

## Sources et licences

- Données du jeu : textes officiels d'Embark Studios, extraits par le projet communautaire
  [baschny/arcraiders-raider-tools](https://github.com/baschny/arcraiders-raider-tools)
  (`scripts/importer_donnees_jeu.py`). Tout le contenu du jeu est © Embark Studios AB.
- Polices : [Prompt](https://fonts.google.com/specimen/Prompt) et
  [Barlow](https://fonts.google.com/specimen/Barlow), SIL Open Font License 1.1
  ([third_party/polices](third_party/polices)).
